package com.cafe.menu;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
class StockService {
    private final JdbcClient db;
    private final TransactionTemplate transactions;

    StockService(JdbcClient db, TransactionTemplate transactions) {
        this.db = db;
        this.transactions = transactions;
    }

    Reservation reserve(String orderId, List<Line> items) {
        List<Line> sorted = items.stream().sorted(Comparator.comparingLong(Line::menuId)).toList();
        if (sorted.stream().map(Line::menuId).distinct().count() != sorted.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 메뉴는 한 항목으로 합쳐 주세요.");
        }

        try {
            return transactions.execute(transaction -> reserveOnce(orderId, sorted));
        } catch (DuplicateKeyException duplicate) {
            Reservation existing = find(orderId);
            List<Line> reserved = existing.items().stream()
                    .map(item -> new Line(item.menuId(), item.quantity())).toList();
            if (!reserved.equals(sorted)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이 주문 ID로 다른 재고 요청이 이미 처리되었습니다.");
            }
            return existing;
        }
    }

    private Reservation reserveOnce(String orderId, List<Line> items) {
        db.sql("INSERT INTO stock_reservations (order_id, status) VALUES (:orderId, 'RESERVED')")
                .param("orderId", orderId).update();

        // Every transaction takes menu locks in ID order.
        for (Line item : items) {
            int updated = db.sql("""
                    UPDATE menu_items SET stock = stock - :quantity
                    WHERE id = :menuId AND active = TRUE AND stock >= :quantity
                    """).param("quantity", item.quantity()).param("menuId", item.menuId()).update();
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "메뉴 " + item.menuId() + "의 재고가 부족하거나 판매할 수 없습니다.");
            }

            int price = db.sql("SELECT price FROM menu_items WHERE id = :menuId")
                    .param("menuId", item.menuId()).query(Integer.class).single();
            db.sql("""
                    INSERT INTO stock_reservation_lines (order_id, menu_id, quantity, unit_price)
                    VALUES (:orderId, :menuId, :quantity, :price)
                    """).param("orderId", orderId).param("menuId", item.menuId())
                    .param("quantity", item.quantity()).param("price", price).update();
        }
        return find(orderId);
    }

    Reservation find(String orderId) {
        return load(orderId, false);
    }

    @Transactional
    Reservation release(String orderId) {
        Reservation reservation = load(orderId, true);
        if (reservation.status() == Status.RELEASED) {
            return reservation;
        }

        for (ReservedItem item : reservation.items()) {
            db.sql("UPDATE menu_items SET stock = stock + :quantity WHERE id = :menuId")
                    .param("quantity", item.quantity()).param("menuId", item.menuId()).update();
        }
        db.sql("UPDATE stock_reservations SET status = 'RELEASED' WHERE order_id = :orderId")
                .param("orderId", orderId).update();
        return new Reservation(orderId, Status.RELEASED, reservation.items(), reservation.totalAmount());
    }

    private Reservation load(String orderId, boolean lock) {
        String sql = "SELECT status FROM stock_reservations WHERE order_id = :orderId";
        String status = db.sql(lock ? sql + " FOR UPDATE" : sql)
                .param("orderId", orderId).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문의 재고 차감 기록을 찾을 수 없습니다."));
        List<ReservedItem> items = db.sql("""
                SELECT menu_id, quantity, unit_price FROM stock_reservation_lines
                WHERE order_id = :orderId ORDER BY menu_id
                """).param("orderId", orderId).query((row, index) -> new ReservedItem(
                row.getLong("menu_id"), row.getInt("quantity"), row.getInt("unit_price"))).list();
        long amount = items.stream().mapToLong(item -> (long) item.quantity() * item.unitPrice()).sum();
        return new Reservation(orderId, Status.valueOf(status), items, amount);
    }

    record Line(@NotNull @Positive Long menuId, @NotNull @Positive @Max(10_000) Integer quantity) {}
    record ReservedItem(long menuId, int quantity, int unitPrice) {}
    record Reservation(String orderId, Status status, List<ReservedItem> items, long totalAmount) {}
    enum Status { RESERVED, RELEASED }
}

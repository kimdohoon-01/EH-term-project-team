package com.cafe.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
class PaymentController {
    private static final String ORDER_ID = "[a-z0-9_-]{1,64}";
    private static final String RESULT_COLUMNS = "order_id, amount, status, processed_at";
    private static final RowMapper<Payment> PAYMENT = (row, index) -> {
        Status status = Status.valueOf(row.getString("status"));
        return new Payment(row.getString("order_id"), row.getLong("amount"), status,
                status == Status.FAILED ? "SIMULATED_DECLINE" : null,
                row.getTimestamp("processed_at").toInstant());
    };

    private final JdbcClient db;

    PaymentController(JdbcClient db) {
        this.db = db;
    }

    @PostMapping
    Payment pay(@Valid @RequestBody PayRequest request) {
        Status result = request.simulate() == Simulation.FAILURE ? Status.FAILED : Status.SUCCEEDED;
        try {
            db.sql("INSERT INTO payments (order_id, amount, status) VALUES (:orderId, :amount, :status)")
                    .param("orderId", request.orderId()).param("amount", request.amount())
                    .param("status", result.name()).update();
        } catch (DuplicateKeyException duplicate) {
            Payment existing = find(request.orderId());
            if (existing.amount() != request.amount()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이 주문 ID로 다른 금액의 결제가 이미 처리되었습니다.");
            }
            return existing;
        }
        return find(request.orderId());
    }

    @GetMapping("/{orderId}")
    Payment find(@PathVariable @Pattern(regexp = ORDER_ID) String orderId) {
        return db.sql("SELECT " + RESULT_COLUMNS + " FROM payments WHERE order_id = :orderId")
                .param("orderId", orderId).query(PAYMENT).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결제 기록을 찾을 수 없습니다."));
    }

    @GetMapping
    List<Payment> list() {
        return db.sql("SELECT " + RESULT_COLUMNS + " FROM payments ORDER BY processed_at DESC, order_id LIMIT 100")
                .query(PAYMENT).list();
    }

    record PayRequest(
            @NotNull @Pattern(regexp = ORDER_ID) String orderId,
            @NotNull @Positive @Max(500_000_000_000L) Long amount,
            Simulation simulate) {}

    record Payment(String orderId, long amount, Status status, String failureReason, Instant processedAt) {}
    enum Status { SUCCEEDED, FAILED }
    enum Simulation { SUCCESS, FAILURE }
}

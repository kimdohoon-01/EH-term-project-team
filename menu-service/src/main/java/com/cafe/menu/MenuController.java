package com.cafe.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
class MenuController {
    private static final RowMapper<MenuItem> MENU = (row, index) -> new MenuItem(
            row.getLong("id"), row.getString("name"), row.getInt("price"),
            row.getLong("stock"), row.getBoolean("active"));

    private final JdbcClient db;

    MenuController(JdbcClient db) {
        this.db = db;
    }

    @GetMapping
    List<MenuItem> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return db.sql("""
                SELECT id, name, price, stock, active FROM menu_items
                WHERE active = TRUE OR :includeInactive = TRUE
                ORDER BY id
                """).param("includeInactive", includeInactive).query(MENU).list();
    }

    @GetMapping("/{id}")
    MenuItem find(@PathVariable long id) {
        return db.sql("SELECT id, name, price, stock, active FROM menu_items WHERE id = :id")
                .param("id", id).query(MENU).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MenuItem create(@Valid @RequestBody CreateMenu request) {
        var key = new GeneratedKeyHolder();
        db.sql("INSERT INTO menu_items (name, price, stock, active) VALUES (:name, :price, :stock, TRUE)")
                .param("name", request.name().strip()).param("price", request.price())
                .param("stock", request.stock()).update(key, "id");
        return find(key.getKey().longValue());
    }

    @PutMapping("/{id}")
    MenuItem edit(@PathVariable long id, @Valid @RequestBody EditMenu request) {
        db.sql("UPDATE menu_items SET name = :name, price = :price, active = :active WHERE id = :id")
                .param("name", request.name().strip()).param("price", request.price())
                .param("active", request.active()).param("id", id).update();
        return find(id);
    }

    @PostMapping("/{id}/stock-adjustments")
    MenuItem adjustStock(@PathVariable long id, @Valid @RequestBody StockAdjustment request) {
        int updated = db.sql("""
                UPDATE menu_items SET stock = stock + :delta
                WHERE id = :id AND stock + :delta >= 0
                """).param("delta", request.delta()).param("id", id).update();
        MenuItem item = find(id);
        if (updated == 0 && request.delta() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "조정할 재고가 부족합니다.");
        }
        return item;
    }

    record MenuItem(long id, String name, int price, long stock, boolean active) {}

    record CreateMenu(
            @NotBlank @Size(max = 80) String name,
            @NotNull @Min(1) @Max(1_000_000) Integer price,
            @NotNull @Min(0) @Max(1_000_000) Integer stock) {}

    record EditMenu(
            @NotBlank @Size(max = 80) String name,
            @NotNull @Min(1) @Max(1_000_000) Integer price,
            @NotNull Boolean active) {}

    record StockAdjustment(@NotNull @Min(-1_000_000) @Max(1_000_000) Integer delta) {}
}

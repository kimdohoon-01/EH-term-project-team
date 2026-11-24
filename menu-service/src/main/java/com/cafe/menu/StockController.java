package com.cafe.menu;

import com.cafe.menu.StockService.Line;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock/reservations")
class StockController {
    private static final String ORDER_ID = "[a-z0-9_-]{1,64}";
    private final StockService stock;

    StockController(StockService stock) {
        this.stock = stock;
    }

    @PutMapping("/{orderId}")
    StockService.Reservation reserve(@PathVariable @Pattern(regexp = ORDER_ID) String orderId,
                                     @Valid @RequestBody ReserveStock request) {
        return stock.reserve(orderId, request.items());
    }

    @GetMapping("/{orderId}")
    StockService.Reservation find(@PathVariable @Pattern(regexp = ORDER_ID) String orderId) {
        return stock.find(orderId);
    }

    @PostMapping("/{orderId}/release")
    StockService.Reservation release(@PathVariable @Pattern(regexp = ORDER_ID) String orderId) {
        return stock.release(orderId);
    }

    record ReserveStock(@NotEmpty @Size(max = 50) List<@NotNull @Valid Line> items) {}
}

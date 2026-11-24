package com.orderandgo.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 서비스끼리 주고받는 JSON 규격. docs/API.md와 함께 변경하세요. */
public final class Contracts {
    private Contracts() {}
    public record UserView(String id, String email, String name) {}
    public record RegisterRequest(@NotBlank @Email @Size(max=120) String email,
        @NotBlank @Size(min=8,max=72) String password, @NotBlank @Size(min=2,max=30) String name,
        @NotBlank @Size(min=5,max=200) String address, @Size(max=100) String addressDetail) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record AuthResponse(String accessToken, UserView user) {}
    public record MenuItem(String id, String name, String category, String description, int price, long stock) {}
    public record CartItem(@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String menuId, @Min(1) @Max(20) int quantity) {}
    public record OrderRequest(@NotEmpty @Size(max=20) List<@Valid CartItem> items,
        @NotBlank @Pattern(regexp="CARD|TEST_DECLINE") String paymentMethod) {}
    public record OrderLine(String menuId, String name, int price, int quantity) {}
    public record ReserveRequest(@NotBlank String orderId, @NotEmpty @Size(max=20) List<@Valid CartItem> items) {}
    public record Reservation(String orderId, String status, List<OrderLine> items, int total) {}
    public record PaymentRequest(@NotBlank String orderId, @Min(1) int amount,
        @NotBlank @Pattern(regexp="CARD|TEST_DECLINE") String method) {}
    public record PaymentResult(String orderId, String status, String paymentId) {}
    public record OrderView(String id, String status, List<OrderLine> items, int total,
        String message, String createdAt, boolean notificationSent) {}
    public record NotificationRequest(@NotBlank @Size(max=100) String eventId,
        @NotBlank String userId, @NotBlank String orderId, @NotBlank String status) {}
    public record NotificationView(String id, String orderId, String title, String message,
        boolean read, String createdAt) {}
    public record ApiError(String code, String message) {}
}

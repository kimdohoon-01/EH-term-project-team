# 통합 API 계약

## 브라우저 → 오더 (8080)

| 메서드·경로 | 내용 |
|---|---|
| POST /api/auth/register | email, password, name, address, addressDetail(선택) → accessToken, user |
| POST /api/auth/login | email, password → accessToken, user |
| GET /api/auth/me | 현재 회원 |
| DELETE /api/auth/session | 로그아웃 |
| GET /api/menus | 활성 메뉴 |
| POST /api/orders | 주문 생성 |
| GET /api/orders | 내 주문 목록 |
| GET /api/orders/{id} | 내 주문 |
| GET /api/notifications | 내 알림 |
| PATCH /api/notifications/{id}/read | 읽음 처리 |

회원 전용 요청은 `Authorization: Bearer {accessToken}`을 사용합니다. 주문에는 8~100자의 영문·숫자·밑줄·하이픈으로 된 `Idempotency-Key`가 필요합니다. 같은 주문의 재시도에는 같은 키·본문을 사용합니다. 키를 재사용하면서 본문을 바꾸면 409입니다.

```json
{"items":[{"menuId":"1","quantity":2}],"paymentMethod":"CARD"}
```

브라우저에서 메뉴 ID는 양의 정수 문자열입니다. 최대 20종, 종류별 1~20개를 주문합니다. 가격은 받지 않고 재고 예약 결과로 확정합니다. 로컬 시연용 `TEST_DECLINE`은 team 프로필에서는 거절됩니다.

주문 접수 후 결과는 HTTP 200의 status로 확인합니다. CONFIRMED=확정, CANCELLED=취소, STOCK_PENDING/PAYMENT_PENDING/CANCEL_PENDING=재확인 중입니다. LEGACY_REVIEW는 이전 대체 서비스에서 미완료 상태로 남은 주문입니다.

## 오더 → 규아 메뉴 (8082)

GET `/api/menus` 응답:

```json
[{"id":1,"name":"아메리카노","price":3500,"stock":30,"active":true}]
```

오더는 ID를 문자열로 변환하고 화면용 category="MENU", description=""를 붙입니다. 규아 원본에는 카테고리가 없으므로 화면은 전체 메뉴만 표시합니다.

PUT `/api/stock/reservations/{orderId}` 요청:

```json
{"items":[{"menuId":1,"quantity":2}]}
```

응답:

```json
{"orderId":"주문 UUID","status":"RESERVED","items":[{"menuId":1,"quantity":2,"unitPrice":3500}],"totalAmount":7000}
```

이름은 차감 전 GET `/api/menus/{id}`로 조회하고, 가격·합계는 예약 응답을 검증한 뒤 저장합니다. 같은 주문 ID와 동일 품목·수량은 추가 차감하지 않습니다. 재고 부족은 409이며 여러 품목 차감은 한 트랜잭션입니다.

POST `/api/stock/reservations/{orderId}/release`는 본문 없이 재고를 복구합니다. 응답 status=RELEASED를 확인한 뒤 주문을 취소합니다. 반복 호출해도 한 번만 복구합니다. GET `/api/stock/reservations/{orderId}`로 확인할 수 있습니다. 메뉴 서비스 자체는 결제 상태를 확인하지 않으므로 오더가 명확한 결제 실패 후에만 복구를 요청합니다.

메뉴 관리: POST `/api/menus` (name, price, stock), PUT `/api/menus/{id}` (name, price, active), POST `/api/menus/{id}/stock-adjustments` (delta). GET 목록의 includeInactive=true는 비활성 메뉴도 포함합니다.

## 오더 → 규아 페이먼트 (8084)

POST `/api/payments`:

```json
{"orderId":"주문 UUID","amount":7000,"simulate":"SUCCESS"}
```

```json
{"orderId":"주문 UUID","amount":7000,"status":"SUCCEEDED","failureReason":null,"processedAt":"2026-11-16T00:00:00Z"}
```

CARD는 SUCCESS, TEST_DECLINE은 FAILURE로 변환합니다. SUCCEEDED는 내부 APPROVED, FAILED는 내부 DECLINED로 변환합니다. 주문 ID·금액이 다르거나 응답이 없으면 바로 취소하지 않습니다. 알 수 없는 상태도 확인 중으로 유지합니다.

같은 주문 ID·금액의 재요청은 기존 결과를 반환합니다. simulate를 바꿔도 기존 결과가 유지됩니다. 금액이 바뀌면 409입니다. GET `/api/payments/{orderId}`로 조회합니다. 실제 PG를 호출하지 않는 시연용 구현입니다.

## 오더 → 알림 (8083)

POST `/internal/notifications`: eventId, userId, orderId, status(CONFIRMED/CANCELLED). 같은 eventId는 한 번 저장합니다. GET `/internal/notifications?userId=...`, PATCH `/internal/notifications/{id}/read?userId=...`는 오더가 인증한 회원 ID로 호출합니다.

도훈 서비스의 `/internal/*`는 X-Internal-Key를 사용합니다. 규아 메뉴·페이먼트 API에는 이 인증이 없으며 내부 키를 전송하지 않습니다. 기본 로컬 실행은 모든 서비스를 127.0.0.1에 바인딩합니다.

회원가입: address는 5~200자, addressDetail은 선택이며 최대 100자입니다. 비밀번호는 ASCII 영문·숫자·특수문자를 포함하고 공백 없는 8~72자입니다. 기존 회원 로그인에는 새 규칙을 소급 적용하지 않습니다. 주소는 user_addresses에 저장하고 주문·알림 응답에 포함하지 않습니다.

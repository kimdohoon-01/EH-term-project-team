# 카페 포장 미리 주문 · 메뉴와 페이먼트

규아 담당인 메뉴 서비스와 페이먼트 서비스를 Java 17과 Spring Boot로 구현한다. 메뉴는 HTML 관리 화면과 API를 제공하고 페이먼트는 결제 API를 제공한다. 쿠버네티스에서는 서비스별 MySQL DB와 PVC를 사용한다. 로컬 Java 실행은 서비스별 H2 파일 DB를 사용한다.

| 서비스 | 포트 | 기능 |
| --- | --- | --- |
| 메뉴 | 8080 | 메뉴 등록·조회·수정·판매 중지, 재고 확인·조정·차감·복구 |
| 페이먼트 | 8080 | 모의 결제 성공·실패 처리, 결과 저장·조회 |

MySQL은 쿠버네티스 내부에서 각 DB의 3306 포트를 사용한다. 메뉴·재고·복구 기록은 `cafe_menu`에, 주문별 결제 결과는 `cafe_payment`의 `payments`에 저장한다. 웹 화면과 서비스 API의 접속 포트는 모두 8080이다.

## 로컬 실행

Java 17이 설치된 상태에서 프로젝트 루트에서 실행한다.

```powershell
.\mvnw.cmd clean package
```

빌드에는 API 테스트가 포함된다. 앱 하나만 직접 확인하려면 아래 명령 중 하나를 실행한다. 메뉴는 [로컬 화면](http://localhost:8080/)으로, 페이먼트는 [결제 API](http://localhost:8080/api/payments)로 확인한다. 두 앱을 함께 확인할 때는 아래 쿠버네티스 배포 방법을 사용한다.

```powershell
java -jar .\menu-service\target\menu-service.jar
java -jar .\payment-service\target\payment-service.jar
```

## Docker 이미지와 쿠버네티스 배포

Docker Desktop의 Linux 엔진과 `docker-desktop` 쿠버네티스 클러스터를 켠 후, 프로젝트 루트에서 실행한다. 쿠버네티스 노드에서 아래 로컬 이미지를 볼 수 있어야 한다. 별도 클러스터에는 접근 가능한 레지스트리에 이미지를 올린 후 Deployment의 `image` 값을 해당 이미지 주소로 지정한다.

```powershell
docker build -f menu-service/Dockerfile -t menu-service:local .
docker build -f payment-service/Dockerfile -t payment-service:local .
.\k8s\deploy-local.ps1
```

배포 스크립트가 `cafe-pickup` 네임스페이스와 DB별 Secret을 만들고, MySQL, 앱, 웹 게이트웨이를 순서대로 배포한다. 기존 DB Secret은 재사용한다. PVC는 클러스터의 기본 StorageClass로 PV를 할당받는다.

Docker Desktop에서 배포가 끝나면 `LoadBalancer` 게이트웨이가 PC의 8080에서 요청을 받는다. 메뉴와 결제 API는 아래 주소로 바로 접속한다.

[메뉴와 재고 화면](http://localhost:8080/menu/) · [결제 기록 API](http://localhost:8080/api/payments)

같은 네트워크에 있는 팀원은 배포한 PC의 IPv4 주소와 8080 포트를 사용한다. Windows 방화벽에서 해당 네트워크의 TCP 8080 연결이 허용되어 있어야 한다. 별도 클러스터에서 배포하는 경우 해당 클러스터의 로드밸런서가 할당한 외부 주소를 사용한다.

실습과 같은 NodePort 접속에는 `cafe-gateway-nodeport` 서비스를 사용한다. 이 Docker Desktop 환경에서는 [노드 포트 메뉴 화면](http://localhost:30080/menu/)을 열 수 있다. 쿠버네티스 노드 주소에 직접 접속할 수 있는 환경에서는 `http://<노드 IP>:30080/menu/`를 사용한다. 노드 포트 30080과 PC의 8080은 같은 게이트웨이 Pod의 8080으로 연결된다.

같은 태그로 이미지를 다시 빌드했으면 앱을 다시 띄운다.

```powershell
kubectl --context docker-desktop -n cafe-pickup rollout restart deployment/menu-service deployment/payment-service
```

## 오더 서비스 연동

오더 서비스가 재고 차감과 결제 호출을 순서대로 진행한다. 결제 상태가 `FAILED`이면 오더 서비스가 같은 주문 ID로 복구를 요청한다. 아래 URL은 같은 네임스페이스의 Pod에서 호출하는 기준이다. `orderId`는 소문자 영문·숫자·밑줄·하이픈 1~64자다.

| 순서 | 요청 | 본문 예시 | 응답 |
| --- | --- | --- | --- |
| 1 | `PUT http://menu-service:8080/api/stock/reservations/demo-001` | `{"items":[{"menuId":1,"quantity":2}]}` | `status`, 품목별 `unitPrice`, `totalAmount` |
| 2 | `POST http://payment-service:8080/api/payments` | `{"orderId":"demo-001","amount":8000,"simulate":"FAILURE"}` | `status`, `amount`, `failureReason`, `processedAt` |
| 3 | `POST http://menu-service:8080/api/stock/reservations/demo-001/release` | 없음 | `status: RELEASED` |

- `GET /api/menus`에서 판매 중인 메뉴와 현재 재고를 조회할 수 있다. 재고 요청이 `RESERVED`일 때 결제를 요청한다. 판매 중지 또는 수량 부족이면 `409`이며, 요청에 여러 메뉴가 있어도 차감한 수량은 모두 되돌아간다.
- 결제 금액은 재고 응답의 `totalAmount`를 그대로 사용한다. 금액 단위는 원이다. `simulate`를 생략하거나 `SUCCESS`로 보내면 성공한다. 모의 결제의 성공과 실패 모두 HTTP `200`이며 본문의 `SUCCEEDED` 또는 `FAILED`로 구분한다.
- 재고 차감이나 결제 기록이 저장된 경우 같은 주문 ID와 같은 내용으로 재요청하면 저장된 결과를 반환한다. 이미 차감했던 품목·수량이나 결제 금액을 바꾸면 `409`다. 복구 재요청은 재고를 한 번만 올린다.
- `GET /api/stock/reservations/{orderId}`와 `GET /api/payments/{orderId}`로 처리 결과를 확인할 수 있다. 재고 차감 기록을 복구하면 같은 주문 ID의 상태는 `RELEASED`로 유지된다.
- 다른 네임스페이스에서 연동할 때는 `menu-service.cafe-pickup`과 `payment-service.cafe-pickup`을 사용한다.
- 팀원 PC에서 연동할 때는 웹 게이트웨이의 `http://<내 PC의 IPv4 주소>:8080` 뒤에 같은 `/api/...` 경로를 붙인다.

### 주문 서비스 없이 시연하기

1. 메뉴 화면에서 가격 4,000원, 초기 재고 5개인 메뉴를 등록한다.
2. 사용한 적 없는 주문 ID로 2개를 차감한다. 재고 3개와 결제 금액 8,000원을 확인한다.
3. 새 PowerShell 창에서 아래 명령의 주문 ID를 실제 차감에 쓴 ID로 바꿔 실행한다. 결제 API가 `FAILED`를 반환하는지 확인한다.
4. 메뉴 화면에서 같은 주문 ID로 재고를 복구한다. 재고가 5개로 돌아오고 복구 버튼을 다시 눌러도 5개로 유지되는지 확인한다.
5. 새 주문 ID로 2개를 차감하고, 그 ID와 `simulate = 'SUCCESS'`로 결제를 요청한다. 결제 API가 `SUCCEEDED`를 반환하고 재고가 3개로 유지되는지 확인한다.

```powershell
$body = @{ orderId = 'demo-failure-01'; amount = 8000; simulate = 'FAILURE' } | ConvertTo-Json
Invoke-RestMethod 'http://localhost:8080/api/payments' -Method Post -ContentType 'application/json' -Body $body
```

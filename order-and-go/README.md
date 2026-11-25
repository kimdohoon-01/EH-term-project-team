# 오더앤고 — 카페 포장 미리 주문 서비스

김도훈의 유저·오더·알림과 이규아의 메뉴·페이먼트를 연결한 통합 버전입니다.

## 시작하기

VS Code에서 팀 저장소를 열고 터미널에서 실행합니다.

```powershell
.\order-and-go\start-local.cmd -SeedDemo
```

JDK 17과 최초 의존성 다운로드를 위한 인터넷 연결이 필요합니다. `ORDER & GO is ready`가 나오면 주문 화면 http://localhost:8080 을 엽니다. 메뉴 관리는 http://localhost:8082 입니다. `-SeedDemo`는 메뉴가 하나도 없을 때만 샘플 6개를 추가합니다. 이미 있는 메뉴는 변경하지 않습니다.

회원가입 → 메뉴 담기 → 주문으로 시연합니다. 장바구니의 발표용 결제 시연에서 실패를 선택하면 재고 복구·취소·알림을 확인할 수 있습니다. 페이먼트는 규아가 만든 모의 결제이며 실제 카드 청구는 없습니다.

이후 명령 예시는 팀 저장소 안의 `order-and-go` 폴더에서 실행합니다.

```powershell
.\stop-local.cmd
.\start-local.cmd -SkipBuild
```

코드를 수정했다면 `-SkipBuild`를 빼고 실행하세요. 실행 중인 JAR를 다시 빌드하면 Windows 파일 잠금으로 실패할 수 있으므로 먼저 종료합니다.

## 서비스와 담당

| 폴더 | 담당 | 포트 | 기능 |
|---|---|---|---|
| user-service | 김도훈 | 8081 | 회원가입·로그인·세션 |
| order-service | 김도훈 | 8080 | 주문 화면·주문 처리·재시도 |
| notification-service | 김도훈 | 8083 | 알림 저장·조회·읽음 |
| ../menu-service | 이규아 | 8082 | 메뉴 관리·재고 차감·복구 |
| ../payment-service | 이규아 | 8084 | 모의 결제·결과 저장 |

`common`은 도훈 서비스의 공통 코드입니다. 팀 저장소 루트의 Maven `integration` 프로필로 다섯 서비스를 함께 빌드합니다.

## DB

설정 전에는 서비스별 H2 파일 DB를 `.data/`에 저장합니다. 종료해도 데이터는 유지됩니다. HeidiSQL의 MySQL 3306을 쓰려면 다음 명령을 실행하고 PC 터미널에 관리자 비밀번호를 입력합니다.

```powershell
.\setup-mysql.cmd
```

이전에 구버전 MySQL 설정을 했다면 ` .\setup-mysql.cmd -Upgrade `를 사용합니다. 설정 후에는 시작 스크립트가 자동으로 MySQL을 선택합니다. MySQL의 메뉴는 관리 화면에서 만들거나 재시작 시 `-SeedDemo`로 추가하세요. H2와 MySQL 사이의 데이터 자동 이전은 없습니다. 자세한 내용은 docs/MYSQL.md를 참고하세요.

## 주문 흐름

주문 저장 → 메뉴 서비스의 재고 차감 → 페이먼트 결제 → 성공 시 주문 확정 → 알림 저장.

명확한 결제 실패 시 재고 복구를 확인한 다음 주문을 취소합니다. 결제 응답이 유실되면 결과 확인 상태를 유지하고 동일 주문 ID로 재시도합니다. 재고·결제는 주문 ID, 주문 생성은 Idempotency-Key, 알림은 eventId로 중복 처리를 막습니다.

기존 대체 서버에서 미완료 상태로 남은 주문은 `LEGACY_REVIEW`로 표시하고 새 결제 서비스로 자동 재전송하지 않습니다. 기존 완료 주문과 회원 데이터는 유지됩니다. 이전 대체 서버의 메뉴·결제 기록은 규아의 DB로 자동 이전하지 않습니다.

## 검증과 팀 연결

```powershell
.\start-local.cmd -BuildOnly
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

통합 검사는 19080~19084와 별도 메모리 DB를 사용하고 테스트 서버를 종료합니다. docs/VALIDATION.md에 확인 범위를 기록했습니다. 실제 API 계약은 docs/API.md를 참고하세요.

규아 서비스가 별도로 실행 중이면 `-Profile team`으로 도훈의 세 서비스만 실행합니다. 이때 MENU_SERVICE_URL / PAYMENT_SERVICE_URL로 주소를 지정할 수 있으며 테스트 결제 선택은 비활성화됩니다. 기본값은 127.0.0.1:8082 / 8084입니다.

로컬 수업 시연용이며 주문 서비스 한 인스턴스를 전제로 합니다. 메뉴·페이먼트의 관리 API에는 인증이 없으므로 Windows 기본 실행은 이 PC의 127.0.0.1에 바인딩합니다. 사용자 취소·환불·실제 PG·SMS·푸시는 구현 범위 밖입니다. 다섯 서비스의 Docker·쿠버네티스 배포는 [팀 저장소 README](../README.md#docker-이미지와-쿠버네티스-배포)를 참고하세요.

Git에는 소스만 공유하세요. `.data`, `.local`, `.logs`, `.maven`, `data`, 모든 `target`은 제외합니다.

## 회원가입 주소·비밀번호 업데이트
회원가입에 기본주소(필수), 상세주소(선택), 비밀번호 확인을 추가했습니다. 비밀번호는 영문·숫자·특수문자 포함, 공백 없는 8~72자입니다. 주소는 user_addresses 테이블에 저장됩니다. 기존 회원과 비밀번호는 유지됩니다.
기존 MySQL 설정이 있는 PC는 이 프로젝트 폴더에서 setup-mysql.cmd -Upgrade를 실행하세요. 관리자 비밀번호는 PC 터미널에서 입력합니다. 주소 테이블을 추가하고 재빌드·실행하며, 기존 데이터는 삭제하지 않습니다. H2는 다음 시작 시 테이블을 생성합니다.

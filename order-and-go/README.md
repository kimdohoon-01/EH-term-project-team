# 오더앤고 — 김도훈 담당 프로젝트

김도훈 담당인 유저·오더·알림 서비스만 담은 프로젝트입니다. 공통 코드(common), 주문 화면, 규아 서비스와 연결하는 HTTP 코드가 포함됩니다.

## 폴더와 역할

| 폴더 | 포트 | 기능 |
|---|---|---|
| user-service | 8081 | 회원가입, 로그인, 내 정보, 로그아웃 |
| order-service | 8080 | 브라우저 화면, 주문 생성·조회, 재고·결제 호출, 실패 복구와 재시도 |
| notification-service | 8083 | 주문 결과 알림 저장·조회·읽음 처리 |
| common | — | 요청·응답 형식, 내부 통신 및 오류 처리 |

메뉴·페이먼트 구현 소스와 기존 대체 서버는 포함하지 않습니다. 주문 기능은 규아가 실행하는 메뉴·페이먼트 API에 연결합니다. API 규격은 docs/API.md를 참고하세요.

## VS Code에서 시작하기

이 README와 pom.xml이 있는 order-and-go-dohoon 폴더를 엽니다. JDK 17이 필요합니다.

```powershell
.\start-local.cmd
```

기본 실행은 도훈의 세 서비스만 시작합니다. 설정 전에는 각 서비스가 H2 파일 DB를 사용합니다. http://localhost:8080 에서 회원가입·로그인을 확인할 수 있습니다. 메뉴 조회와 주문·결제 흐름은 규아 서비스가 실행되어 있어야 합니다.

이미 기존 통합 프로젝트가 실행 중이라면 해당 프로젝트 폴더에서 stop-local.cmd로 종료한 후 실행하세요. 두 프로젝트의 8080·8081·8083 포트가 같기 때문입니다.

규아가 이 PC에서 메뉴 8082, 페이먼트 8084를 실행하면 기본 주소로 연결됩니다. 다른 주소라면 실행 전에 지정합니다.

```powershell
$env:MENU_SERVICE_URL='http://규아서버주소:8082'
$env:PAYMENT_SERVICE_URL='http://규아서버주소:8084'
.\start-local.cmd
```

다른 PC와 통신할 경우 해당 서비스의 접속 허용 주소·포트 설정도 함께 맞춰야 합니다. 기본 로컬 서버는 127.0.0.1에만 바인딩합니다. 결제 실패 시연이 필요하면 -Profile local로 실행합니다. 이 경우에도 세 서비스만 시작하며 TEST_DECLINE 요청을 규아의 FAILURE 시뮬레이션으로 변환합니다.

## MySQL / HeidiSQL

```powershell
.\setup-mysql.cmd
```

HeidiSQL에 사용하는 관리자 비밀번호를 PC 터미널에 입력합니다. 이 프로젝트는 orderandgo_user, orderandgo_order, orderandgo_notification 세 DB와 전용 계정을 준비합니다. 이미 같은 이름의 DB가 있다면 기존 데이터를 유지합니다. 규아의 cafe_menu·cafe_payment는 규아 쪽에서 관리합니다.

기존 통합 프로젝트의 PC 전용 암호화 설정과 DB 파일은 이 복사본에 포함하지 않았습니다. 이 폴더에서는 MySQL 설정 명령을 한 번 실행하세요. 설정 후에는 start-local.cmd가 MySQL을 자동 선택합니다. H2 데이터가 MySQL로 자동 이전되지는 않습니다.

## 빌드·종료

```powershell
.\start-local.cmd -BuildOnly
.\stop-local.cmd
.\start-local.cmd -SkipBuild
```

BuildOnly는 세 서비스와 공통 코드를 테스트·빌드합니다. 규아 서비스 없이 실행할 수 있는 테스트 17개가 포함됩니다. 실제 메뉴·페이먼트를 포함한 통합 검사는 원래 통합 프로젝트에서 수행합니다.

프로젝트를 Git 저장소에 넣을 때 .local, .data, .logs, .maven, target 폴더는 제외하세요. 실제 PG 결제, 사용자 취소·환불은 구현 범위에 포함하지 않습니다. 주문 처리는 오더 서비스 한 인스턴스를 전제로 합니다.

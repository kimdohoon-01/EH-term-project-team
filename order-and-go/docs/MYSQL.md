# HeidiSQL / MySQL 3306 연결

현재 PC의 MySQL 8.0, 호스트 `127.0.0.1`, 포트 `3306`, 관리자 `root`를 기준으로 준비했습니다. HeidiSQL은 이 MySQL 서버의 테이블을 확인하는 프로그램입니다.

## 처음 한 번

`order-and-go` 폴더의 VS Code 터미널에서:

```powershell
.\setup-mysql.cmd
```

`MySQL administrator password (hidden)`이 나오면 **HeidiSQL에서 접속할 때 사용하는 root 비밀번호**를 입력하고 Enter를 누릅니다. 입력한 글자는 화면에 표시되지 않습니다. 채팅에 비밀번호를 보낼 필요가 없습니다.

이 작업은 순서대로 다음을 수행합니다.

1. 실행 중인 이 프로젝트의 서버를 종료하고 MySQL 드라이버를 포함해 빌드합니다.
2. MySQL에 접속하여 아래 DB와 테이블을 생성합니다. 같은 이름의 DB·테이블이 있으면 유지합니다.
3. 임의 이름의 프로젝트 전용 계정(`orderandgo_...`)을 만들고 다섯 DB의 SELECT·INSERT·UPDATE·DELETE 권한만 부여합니다.
4. **전용 계정** 비밀번호를 현재 Windows 사용자만 복호화할 수 있도록 암호화해 `.local/mysql.json`에 저장합니다. root 비밀번호는 저장하지 않습니다.
5. 서비스를 MySQL 모드로 실행합니다.

`ORDER & GO is ready`가 뜨면 http://localhost:8080 에서 회원가입하고 주문해 보세요. 이제 HeidiSQL에서 실제 데이터가 보입니다.

## HeidiSQL에서 보일 DB

| DB | 테이블 | 담당 |
|---|---|---|
| `orderandgo_user` | `app_users`, `sessions`, `user_addresses` | 도훈 |
| `orderandgo_order` | `cafe_orders`, `order_lines`, `order_backends` | 도훈 |
| `orderandgo_notification` | `notifications` | 도훈 |
| `cafe_menu` | `menu_items`, `stock_reservations`, `stock_reservation_lines` | 규아 |
| `cafe_payment` | `payments` | 규아 |

3306에 접속한 HeidiSQL에서 DB 목록을 새로고침하면 확인할 수 있습니다. 주문 후 `cafe_orders`의 `status`와 `order_lines`의 품목·수량을 확인하세요. 앱의 회원 비밀번호와 로그인 토큰은 해시로 저장됩니다.

기존 버전의 MySQL 설정이 있다면 ` .\setup-mysql.cmd -Upgrade `로 새 테이블과 다섯 DB의 전용 계정을 준비합니다. 기존 설정은 .local에 백업하고 기존 데이터는 삭제하지 않습니다. 예전 orderandgo_partner DB는 더 이상 사용하지 않습니다.

메뉴는 http://localhost:8082 에서 등록하거나, 서버 종료 후 ` .\start-local.cmd -SeedDemo `로 빈 DB에 샘플을 넣습니다.

## 다시 실행하기

```powershell
.\stop-local.cmd
.\start-local.cmd
```

`.local/mysql.json`이 있으면 자동으로 MySQL에 연결합니다. H2로 돌아가서 기존 연습 데이터를 보려면 ` .\start-local.cmd -Database h2 `를 사용합니다.

**처음 만든 MySQL DB는 새 데이터 저장소입니다. 기존 H2 회원·주문 데이터는 자동 이전되지 않습니다.** H2 파일은 `.data/`에 유지됩니다. MySQL에서는 새로 회원가입해서 사용하면 됩니다.

## 파일 설명

- `docs/mysql-init.sql`: HeidiSQL에서도 읽고 실행할 수 있는 DB·테이블 생성 SQL. 삭제 명령을 포함하지 않습니다.
- 도훈 서비스의 `schema-mysql.sql`, 규아 서비스의 `schema.sql`: 해당 서비스의 테이블 구조.
- 각 서비스의 `application-mysql.properties`: MySQL JDBC 연결 프로필.
- `scripts/SetupMysql.java`: 관리자 인증·DB 생성·제한된 전용 계정 생성. 앱은 DDL 권한 없이 실행됩니다.
- `.local/mysql.json`: PC 전용 암호화 설정. GitHub나 팀원에게 공유하지 않습니다. 다른 PC에서는 `setup-mysql.cmd`를 다시 실행합니다.

`.local`, `.data`, `.logs`, `.maven`, `target`은 Git에서 제외됩니다. 데이터베이스 계정을 만드는 권한이 없는 수업용 계정을 사용하는 경우에는 관리자에게 DB와 앱 계정 생성을 요청해야 합니다.

설정 실패 시 `ERROR 1045` / code 1045는 관리자 계정·비밀번호를 확인하세요. 서버 실행 중 포트 오류가 나면 해당 프로젝트에서 `stop-local.cmd`를 먼저 실행합니다. root 인증이 실패하면 DB 설정 파일을 저장하지 않습니다.

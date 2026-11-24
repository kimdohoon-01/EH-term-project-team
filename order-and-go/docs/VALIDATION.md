# 통합 프로젝트 확인 방법

팀 저장소 루트의 Maven `integration` 프로필에서 다섯 서비스의 테스트를 실행합니다. 빌드가 끝나면 별도 포트(19080~19084)와 메모리 H2 DB로 HTTP 통합 검사를 실행할 수 있습니다.

```powershell
.\mvnw.cmd -P integration package
powershell -NoProfile -ExecutionPolicy Bypass -File .\order-and-go\scripts\smoke-test.ps1
```

통합 검사는 회원가입 입력 검증과 로그인, 주문 확정, 재고 차감과 복구, 모의 결제 실패, 중복 주문 방지, 알림을 확인합니다.

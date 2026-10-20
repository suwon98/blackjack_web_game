# 보안 점검 범위

## 구현한 방어

- HTTP와 WebSocket의 JWT 종류·만료·DB 세션 유효성 확인. refresh 토큰은 access 토큰으로 사용할 수 없습니다.
- refresh 회전 시 행 잠금으로 동시 재사용을 차단합니다. 로그아웃 및 재로그인/회전 시 이전 access 토큰도 무효화합니다.
- 소켓 CONNECT의 방 멤버십, SEND/SUBSCRIBE 목적지, 게임 행위의 본인 차례와 revision을 확인합니다. 퇴장한 구독자에게는 새 게임 스냅샷을 전달하지 않습니다.
- 비공개 방 초대 코드 BCrypt 해시 저장. 비밀번호와 토큰 원문을 로그에 출력하지 않습니다.
- HTTP 요청 16KiB, STOMP 메시지 8KiB 제한. IP별 인증 요청 분당 30회, 일반 API 분당 300회, 소켓 요청 10초당 60회 제한.
- DB 트랜잭션/버전 검사 및 게임 스냅샷 롤백. 중복 정산 방지와 최근 20건 정산 조회.
- 운영 백엔드 비특권 사용자, 읽기 전용 파일시스템, 비밀키 파일, 내부 DB 네트워크. Caddy CSP/HSTS/frame 차단 등 응답 헤더.

## 재현 가능한 검사

```sh
cd blackjack
./gradlew test bootJar
cd ../frontend/frontend-blackjack
npm ci
npm run lint
npm run build
npm audit --omit=dev
node scripts/restart-smoke.mjs
# 8080에서 로컬 서버가 실행 중이어야 함
node scripts/smoke.mjs
node scripts/security-smoke.mjs
```

재시작 테스트는 별도 18080 포트와 `.run/restart-*` DB에서 프로세스를 강제 종료하고 카드/잔액/세션 복원, 정산 후 재시작 및 중복 지급 방지를 확인합니다. 일반 smoke 테스트는 테스트 계정을 남깁니다.

## 운영 전 보완 사항

- 이 점검은 외부 침투 테스트나 모든 취약점이 없다는 보장이 아닙니다. npm 검사 외의 Java·OS 이미지 CVE 스캔, MariaDB 저장·프로세스 종료·재시작 복구는 검증했으며, 장시간 부하 및 디스크 손상 시 복원 시험은 별도 배포 게이트입니다.
- 브라우저 토큰은 기존 localStorage 방식을 유지합니다. CSP와 React 출력 이스케이프를 적용했지만 XSS 발생 시 토큰이 노출될 수 있습니다. 공개 서비스 확대 전 HttpOnly refresh 쿠키와 이에 맞는 CSRF 방어로 이전하는 것이 좋습니다.
- 현재 요청 제한은 단일 서버 메모리 기반입니다. 대규모 공격, 분산 IP, 접속 유지/소켓 수 제한은 reverse proxy/WAF 및 운영 모니터링으로 보완해야 합니다. 공용 NAT 사용자는 인증 제한을 공유합니다.
- 단일 서버만 지원합니다. 다중 인스턴스에는 공유 메시지 브로커, 방 소유권/분산 락, 세션 라우팅 설계가 필요합니다.
- 백업/복원 훈련, 디스크·오류·DB 용량 알림, 정산 데이터 보존 정책과 계정 복구/비밀번호 재설정 절차를 운영 규모에 맞게 추가하세요.
- OAuth는 기본 비활성화이며 실제 Google 자격 증명 흐름은 미검증입니다.

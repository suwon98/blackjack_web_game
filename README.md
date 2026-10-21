# 실시간 멀티플레이 블랙잭

React + TypeScript + Spring Boot 기반의 가상 머니 게임입니다. 실제 결제·환전 기능은 없습니다.

## 바로 실행 (Windows / PowerShell 7)

Node.js 22.12 이상 또는 24, JDK 17 이상과 Java 17 Gradle toolchain이 필요합니다. 현재 환경에서는 Java 21로 Gradle을 실행하고 Java 17 타깃 컴파일을 확인했습니다.

```powershell
cd C:\Users\Master\blackjack\blackjack_web_game-main
.\start-local.ps1
```

빌드 결과와 의존성이 이미 있으면 `.\start-local.ps1 -SkipBuild`를 사용할 수 있습니다. 브라우저에서 http://localhost:5173 에 접속해 회원가입 → 로그인 → 방 입장 → 자리 앉기 → 일반 참가자 준비 완료 → 방장 게임 시작 순서로 진행합니다. 종료는 `.\stop-local.ps1`입니다. 실행 로그는 `.run/`에 저장합니다.

두 명 이상 테스트하려면 일반 창과 시크릿 창 또는 서로 다른 브라우저 프로필을 사용하세요. 같은 브라우저 프로필의 탭은 로그인 저장소를 공유합니다.

### 수동 실행

백엔드 디렉터리 `blackjack`에서 `./gradlew bootRun`, 프론트 디렉터리 `frontend/frontend-blackjack`에서 `npm ci` 후 `npm run dev`를 각각 실행할 수 있습니다. Windows에서는 `gradlew.bat`, `npm.cmd`도 사용할 수 있습니다.

## 저장 및 환경 설정

- 기본 `local` 프로필은 별도 DB 설치 없이 H2 파일에 회원·잔액·게임 스냅샷·세션·정산 내역을 저장합니다 (`blackjack/data/blackjack.mv.db`).
- 서버 재시작 후 진행 중인 게임은 일시정지 상태로 복구됩니다. 로비의 **내 게임 이어하기** → 게임의 **게임 이어하기**를 선택하면 카드·베팅·잔액을 유지하고 제한시간을 다시 시작합니다.
- 게임 상태와 잔액은 같은 트랜잭션으로 저장하며 완료한 라운드를 중복 지급하지 않습니다. 로컬 H2도 즉시 쓰기 설정을 사용합니다.
- 로컬 기본 JWT 키는 개발 전용입니다. 공개 배포에는 사용하지 마세요.
- 운영은 MariaDB + Spring Boot + Caddy Docker Compose 구성입니다. Redis는 필요하지 않습니다. 스키마는 Flyway로 관리하고 DB/백엔드 포트는 외부에 노출하지 않습니다.
- **[배포·비밀키·백업·복원 안내](docs/DEPLOYMENT.md)** 및 **[보안 점검과 남은 보완 사항](docs/SECURITY.md)**를 확인하세요.
- Oracle Cloud와 DuckDNS로 처음 배포한다면 **[가입부터 HTTPS 접속까지](docs/ORACLE-DUCKDNS.md)**를 따르세요.
- AWS와 GitHub Actions 구성은 **[AWS 배포와 변경 전달](docs/AWS-DEPLOYMENT.md)**에서 확인하세요. 원격 배포는 명시적으로 선택한 릴리스에서만 실행합니다.
- 단일 백엔드 인스턴스만 지원합니다. 같은 DB에 두 번째 서버가 쓰는 것은 advisory lock으로 막습니다.

## 게임 규칙

- 대기실과 각 게임방에서 WebSocket 텍스트 채팅 및 6종 이모지 반응을 지원합니다. 방 채팅은 현재 참가자만 송수신할 수 있습니다.
- 채팅은 메시지당 300자, 사용자당 10초에 5회로 제한합니다. 현재 연결에서 받은 최근 100개만 표시하며 대화 기록은 영구 저장하지 않습니다.

- 6덱, 딜러는 soft 17을 포함해 17 이상에서 Stand. 슈가 25% 이하로 남으면 다음 라운드에 교체.
- 가입 시 100,000 가상 머니. 잔액 1,000 미만이면 로비에서 고정 100,000 칩 충전.
- 방 정원 1~6명(관전자 포함). 한 사용자는 한 방에만 입장 가능. 빈 좌석에 앉으면 다음 라운드부터 참여.
- 베팅은 방의 최소액 이상, 100 단위. 10초 미응답 시 최소액 자동 베팅. 잔액 부족 시 다음 라운드에 관전으로 전환.
- 자연 블랙잭 3:2, 일반 승리 1:1, 무승부 원금 반환. 서버가 결과와 지급액을 결정합니다.
- 보험은 딜러의 공개 카드가 A일 때 원래 베팅의 절반. 딜러 블랙잭이면 보험 원금 포함 3배 지급. 보험 선택은 10초.
- 같은 숫자 두 장은 최대 4패까지 스플릿. 스플릿의 21은 자연 블랙잭이 아닙니다. A 스플릿은 각 패 한 장만 받고 Stand.
- 첫 두 장에서 Double Down 가능(스플릿 이후 포함). Surrender는 스플릿 전 첫 두 장에서만 허용하며 원금 절반 반환.
- 행동 제한시간 20초, 미응답 시 현재 패 자동 Stand. 정산 5초 후 다음 라운드.
- 퇴장한 플레이어의 남은 패는 Stand 처리하고 정상 정산합니다. 마지막 플레이어 퇴장 시에도 방 삭제 전 정산합니다.
- 연결이 끊기면 15초 유예 후 자동 퇴장. 새로고침·짧은 연결 단절은 재연결 시 상태를 다시 받습니다.

## 검증

```powershell
# blackjack 디렉터리
.\gradlew.bat test bootJar

# frontend/frontend-blackjack 디렉터리
npm run lint
npm run build
# 서버를 실행한 상태에서 실제 HTTP/STOMP 두 사용자 3라운드 검증
node scripts/smoke.mjs
node scripts/security-smoke.mjs
# 별도 18080 포트에서 서버 강제 종료와 복구 검증 (bootJar 필요)
node scripts/restart-smoke.mjs
```

`smoke.mjs`는 테스트 계정 2개를 생성합니다. 비공개 방 입장, 사용자 위조 방지, 음수 베팅 거절, 게임 진행, 정산 DB 잔액, 토큰 재발급 및 로그아웃을 검증한 뒤 방을 나갑니다. 생성한 테스트 계정은 로컬 DB에 남습니다.

서버 테스트는 독립된 메모리 DB를 사용하며 로컬 플레이 계정에 영향을 주지 않습니다. 점수·정산·보험·스플릿·중복 요청·다른 사용자 액션·퇴장 처리를 검증합니다.

## 선택 기능: Google 로그인

일반 회원가입/로그인에는 Google 설정이 필요 없습니다. Google OAuth는 자격 증명을 제공한 환경에서만 활성화합니다.

- 서버: `APP_OAUTH_ENABLED=true`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=email,profile`.
- Google 콘솔 redirect URI: `http://localhost:8080/login/oauth2/code/google` (배포 시 실제 서버 주소).
- 프론트: `VITE_GOOGLE_LOGIN=true`. 개발 서버를 재시작하세요.
- 콜백 토큰은 URL fragment에서 읽고 주소에서 즉시 제거합니다.
- 실제 Google 인증은 별도 OAuth 자격 증명이 없어 검증 범위에서 제외했습니다.

## 주요 코드

- `blackjack/.../domain/game/service/GameService.java`: 방 잠금, DB 트랜잭션, 검증, 타이머, 정산.
- `blackjack/.../global/security/StompHandler.java`: CONNECT 인증, SEND/SUBSCRIBE 경로 권한.
- `frontend/frontend-blackjack/src/store/useGameStore.ts`: 연결·재연결·스냅샷·액션 전송.
- `frontend/frontend-blackjack/src/pages/GamePage.tsx`: 테이블과 베팅·게임 화면.

Docker Compose에서 MariaDB 저장과 백엔드 강제 종료·DB 재시작 후 게임 복구를 검증했습니다. `./start-compose.ps1`로 실행하고 http://localhost:18081 에서 접속하세요. 공개 도메인 HTTPS 검증은 배포 대상 지정 후 진행합니다.

방장 권한, 준비 상태, 지연 강퇴와 베팅 조절의 규칙 및 배포 순서는 [배포 안내](docs/ROOM-READY-DEPLOYMENT.md)를 참고하세요.

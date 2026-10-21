# 방장 권한과 준비 상태 배포

## 동작 규칙

- 방을 만든 사용자가 방장이며, 착석한 방장만 시작할 수 있습니다.
- 일반 참가자는 착석 후 준비 완료 또는 준비 취소를 선택합니다. 관전자에게는 준비를 요구하지 않습니다.
- 착석한 일반 참가자가 한 명 이상이고 모두 준비하면 방장에게 10초의 시작 시간이 주어집니다. 방장이 미착석이어도 시간이 흐릅니다.
- 일반 참가자가 두 명 이상이고 한 명만 준비하지 않았다면 해당 참가자에게 10초가 주어집니다. 일반 참가자가 한 명뿐이거나 미준비자가 두 명 이상이면 이 강퇴 조건을 적용하지 않습니다.
- 강퇴 또는 자발적 퇴장으로 방장이 사라지면 좌석 번호가 가장 작은 참가자가 방장을 이어받습니다. 착석자가 없으면 관전자 아이디 순으로 위임합니다.
- 강퇴된 사용자는 사유를 확인하고 로비로 돌아갈 수 있습니다. 같은 방 재입장은 60초 동안 제한합니다.
- 준비 상태 변경으로 조건이 해제되면 이전 타이머는 무효입니다. 같은 준비 요청을 반복해도 제한시간을 연장하지 않습니다.
- 준비 검사는 WAITING 상태에 적용합니다. 진행 중 게임과 정산 후 자동 다음 라운드는 기존 동작을 유지합니다.
- 대기 상태로 서버가 재시작되면 방장과 준비 상태를 복구하고 10초를 새로 부여합니다. 진행 중 라운드는 기존처럼 일시정지 상태에서 명시적으로 재개합니다.
- 이전 저장 데이터에 방장이 없으면 좌석 번호순, 관전자 아이디순으로 지정합니다. 이전 데이터에는 원래 생성자 정보가 없어 소급 복원할 수 없습니다.

## 베팅 조절

100, 1,000, 10,000, 50,000, 100,000칩 단위의 더하기와 빼기를 제공합니다. 직접 입력, MAX, 최소 베팅 선택도 지원합니다. 입력란을 벗어나면 100칩 단위로 내림하며, 음수와 잔액 초과 선택을 제한합니다. 최소 베팅 미만 또는 단위에 맞지 않는 금액은 확정할 수 없습니다. 선택만으로 잔액은 바뀌지 않습니다.

## 로컬 검증

```powershell
cd C:\Users\Master\blackjack-github\blackjack
.\gradlew.bat test bootJar --no-daemon
cd ..\frontend\frontend-blackjack
npm test
npm run lint
npm run build
node scripts/restart-smoke.mjs
```

실행 중인 검증 서버에 대해 아래 검사를 수행합니다.

```powershell
$env:API_URL='http://localhost:8080'
node scripts/ready-smoke.mjs
node scripts/smoke.mjs
```

Actions는 MariaDB Compose 환경에서 준비 상태 통합 검사를 함께 실행합니다. 로컬 Docker Desktop 시작 오류가 발생한 이번 점검에서는 실제 소켓 및 강제 종료 복원 검사를 H2 로컬 서버에서 수행했습니다. MariaDB 결과는 PR의 Actions 통과로 확인해야 합니다.

검증 결과: 백엔드 40개 테스트, 베팅 단위 테스트 9개, 린트, 빌드, 실제 소켓 준비/강퇴 검사, 2인 3라운드 정산, 채팅, 인증/접근 제어 및 강제 종료 복원 검사를 통과했습니다.

## PR 만들기

```powershell
cd C:\Users\Master\blackjack-github
git branch --show-current
git status --short
git diff --check
git add README.md .github/workflows/ci.yml blackjack/src frontend/frontend-blackjack/src frontend/frontend-blackjack/scripts docs/ROOM-READY-DEPLOYMENT.md
git diff --cached --stat
git commit -m "Add host readiness controls and adjustable betting"
git push -u origin feat/room-ready-bet-controls
```

GitHub에서 base를 main, compare를 feat/room-ready-bet-controls로 선택합니다.

제목: Add host readiness controls and adjustable betting

설명:

```text
게임 시작을 방장에게 제한하고 일반 참가자의 준비 완료/취소를 추가합니다.
대기 상태의 10초 지연 규칙, 자동 강퇴, 방장 위임, 재입장 제한과 개별 알림을 적용합니다.
방장과 준비 정보를 저장하고 이전 저장 데이터의 호환성을 유지합니다.
베팅 증감 버튼, 직접 입력, 최소 베팅 선택을 추가합니다.

검증: 백엔드 테스트, 프런트엔드 테스트/린트/빌드, 실제 WebSocket 준비/강퇴 통합 검사,
2인 3라운드 정산, 강제 서버 종료 후 게임/잔액 복원.
MariaDB 준비 상태 통합 검사를 Verify blackjack에 추가했습니다.
```

최신 커밋의 Verify blackjack 검사가 모두 성공한 뒤 Merge pull request를 누릅니다. 검사 대기 또는 실패 상태에서는 합치지 않습니다.

## Actions 배포

1. 합쳐진 main의 Verify blackjack 검사가 성공했는지 확인합니다.
2. Actions → Build and deliver → Run workflow에서 main을 선택하고 deploy를 true로 실행합니다.
3. verify, images, deploy가 모두 성공하고 마지막 로그에 Release healthy와 새 커밋 SHA가 나오는지 확인합니다.
4. EC2에서 `sudo cat /opt/blackjack/.release.env` 값이 해당 SHA와 일치하는지 확인합니다.
5. `curl -fsS https://blackjack-sw.duckdns.org/actuator/health`가 UP을 반환하는지 확인합니다.
6. 서로 다른 계정으로 접속해 방장만 시작 가능한지, 준비 상태가 양쪽에 표시되는지 점검합니다.
7. 방장과 일반 참가자 두 명으로 미준비자 강퇴를 확인합니다. 준비한 참가자가 남은 상태에서 방장이 시작하지 않으면 방장 강퇴와 위임을 확인합니다.
8. 베팅 금액을 더하고 뺀 뒤 직접 입력, 최소 베팅, MAX를 확인합니다. 확정 전 잔액은 그대로여야 합니다.
9. 배포 전 기록한 잔액과 배포 후 잔액을 비교합니다. 진행 중 게임은 복구 후 이어하기로 재개합니다.

배포 스크립트는 변경 전 DB 백업을 남깁니다. 새 버전이 쓴 스냅샷에는 추가 필드가 있으므로 이전 이미지로 수동 복귀할 때는 저장 형식의 호환성을 먼저 확인합니다. 진행 중인 새 정산이 있다면 백업을 그대로 덮어쓰지 않습니다.

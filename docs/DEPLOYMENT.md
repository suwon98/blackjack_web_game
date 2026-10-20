# 배포와 복구

Oracle Cloud와 DuckDNS를 처음 설정한다면 [가입부터 HTTPS 접속까지의 단계별 절차](ORACLE-DUCKDNS.md)를 먼저 확인하세요.

대상은 **단일 Linux 서버, 단일 백엔드 인스턴스, MariaDB**입니다. Docker Compose v2, Python 3, 공개 도메인과 해당 서버를 가리키는 DNS A/AAAA 레코드, 인바운드 TCP 80/443이 필요합니다. 프런트와 API는 동일 출처로 제공합니다. 실제 돈을 취급하지 않는 연습 게임입니다.

## 새 서버 배포

프로젝트 루트에서 실행합니다.

```sh
python3 deploy/generate-secrets.py
printf 'DOMAIN=game.your-domain.com\n' > .env
docker compose config --quiet
docker compose build --pull
docker compose up -d
docker compose logs --tail=100 backend web
curl --fail https://game.your-domain.com/actuator/health
```

`your-domain.com`을 실제 소유한 도메인으로 변경합니다. Caddy가 인증서를 발급하고 HTTPS/WSS를 제공합니다. DB와 백엔드는 호스트 포트를 공개하지 않습니다. 배포 후 서로 다른 브라우저에서 가입 → 방 입장 → 착석 → 베팅 → 정산 → 서버 재시작 → 이어하기까지 확인하세요.

비밀키는 `deploy/secrets/` 파일에서 읽습니다. 생성기는 기존 파일을 덮어쓰지 않습니다. Linux에서 디렉터리 권한은 700, 앱이 읽는 두 파일은 444입니다. 이는 호스트 디렉터리 접근을 제한하면서 Docker의 읽기 전용 secret 마운트를 비특권 UID 10001에서 읽도록 하기 위함입니다. DB root 키는 600입니다. 파일을 저장소·이미지·로그에 넣지 마세요. 테스트 PC에서 생성된 키 대신 **배포 호스트에서 새 키를 생성**하세요. DB 생성 이후 비밀번호 파일만 바꾸면 DB 암호는 변경되지 않습니다.

## 저장 방식과 복구 의미

- 회원 잔액, 전체 카드 슈 순서, 딜러/플레이어 카드, 베팅, 보험, 차례, 기한, 라운드 번호, 정산 여부를 DB에 저장합니다.
- 각 게임 변경과 잔액 변경, 정산 내역은 같은 트랜잭션으로 커밋합니다. 저장 실패 시 메모리도 이전 상태로 복원합니다.
- 재시작 시 진행 중이던 게임을 일시정지 상태로 복구합니다. 기존 참가자의 ‘이어하기’로 제한시간을 새로 시작합니다. 오프라인 동안 자동으로 새 베팅을 소비하지 않습니다.
- 로그인 세션도 DB에 저장하므로 같은 JWT 키를 유지하면 재시작 후 사용할 수 있습니다. 토큰 원문 대신 SHA-256 해시를 보관합니다.
- 로컬 H2는 `WRITE_DELAY=0`으로 커밋 시 디스크 동기화를 요청합니다. 운영 MariaDB의 내구성 설정을 임의로 완화하지 마세요.
- MariaDB advisory lock으로 같은 DB에 두 번째 서버가 쓰기를 시작하는 것을 막습니다. DB 연결을 잃으면 재시작해 스냅샷을 다시 읽어야 합니다. 다중 인스턴스 운영은 지원하지 않습니다.
- 이는 프로세스 종료 복구이며, 디스크 손실·손상은 백업 복원이 필요합니다.

## 백업 및 복원

DB volume과 secret 파일을 모두 보호해야 합니다. DB 덤프에는 숨겨진 딜러 카드와 앞으로 뽑을 카드도 포함되어 있으므로 외부 공개하면 안 됩니다.

Linux에서 프로젝트 루트 기준:

```sh
mkdir -p backups
chmod 700 backups
umask 077
docker compose exec -T db sh -c 'MYSQL_PWD="$(cat /run/secrets/db-root-password)" mariadb-dump -uroot --single-transaction --routines --triggers blackjack' > "backups/blackjack-$(date +%Y%m%d-%H%M%S).sql"
```

정기적으로 암호화한 외부 백업과 별도 환경 복원 테스트를 수행하세요. 운영 복원은 쓰기를 멈춘 뒤 실시합니다:

```sh
docker compose stop backend
docker compose exec -T db sh -c 'MYSQL_PWD="$(cat /run/secrets/db-root-password)" mariadb -uroot blackjack' < backups/CHOSEN_BACKUP.sql
docker compose up -d backend
```

복원하면 덤프 시점 이후 진행은 사라집니다. `docker compose down -v`는 영구 데이터를 삭제하므로 일반 종료에 사용하지 마세요.

## 스키마와 업데이트

새 MariaDB에는 Flyway `V1__initial_schema.sql`을 적용하고 Hibernate `validate`로 확인합니다. 이미 테이블이 있는 이전 설치에 그대로 실행하면 안전하게 실패합니다. 기존 운영 DB에 무조건 baseline을 설정하지 말고 백업 후 스키마 차이를 검토해 별도 마이그레이션을 작성하세요. 로컬 H2 파일을 MariaDB volume으로 복사해서 사용할 수는 없습니다.

업데이트 전 테스트와 백업을 수행하고 `docker compose build --pull && docker compose up -d`를 실행합니다. 브라우저 정적 파일은 버전 해시로 배포하며 HTML은 재검증합니다. 이미지 태그는 호환 버전을 지정했으나 불변 digest는 아니므로 실제 릴리스에서 검증한 digest를 고정하는 것을 권장합니다.

## 로컬 컨테이너 검증

Windows PowerShell에서 프로젝트 루트 기준:

```powershell
.\start-compose.ps1
# 이미지가 이미 준비된 경우
.\start-compose.ps1 -SkipBuild
```

`http://localhost:18081`에서 접속합니다. `compose.local.yml`은 포트를 loopback에만 바인딩하고 Caddy를 HTTP로 실행합니다. 기본 배포 구성의 HTTPS 설정과 분리되어 있습니다. 기존 5173 개발 서버와 DB도 별도입니다. 시작 스크립트는 시스템 드라이브 여유 공간과 엔진 응답, 애플리케이션 준비 상태를 확인합니다. Docker 디스크를 다른 드라이브에 설정했다면 해당 드라이브의 여유 공간도 확인하세요.

```powershell
cd frontend/frontend-blackjack
$env:COMPOSE_TEST='1'
node scripts/restart-smoke.mjs
$env:API_URL='http://localhost:18081'
node scripts/smoke.mjs
node scripts/security-smoke.mjs
```

재시작 검증은 `blackjack-verify` 프로젝트의 백엔드를 강제 종료하고, 정산 후에는 DB도 재시작합니다. 해당 환경에서 다른 플레이를 진행하지 않은 상태로 실행하세요. 완료 후 컨테이너는 유지됩니다. 종료는 프로젝트 루트에서 `./stop-compose.ps1`을 사용합니다. 영구 볼륨은 보존됩니다.

Linux에서는 `export DOMAIN=localhost` 후 `docker compose -p blackjack-verify -f compose.yml -f compose.local.yml up -d --build`로 같은 환경을 실행할 수 있습니다. 테스트에는 Node.js와 프런트의 설치된 의존성이 필요합니다.

## 확인된 범위와 남은 배포 게이트

2026-10-20 Docker Engine 29.8.2에서 MariaDB 11.4.13, Spring Boot 운영 프로필, Caddy 컨테이너를 실행했습니다. Flyway V1 적용과 Hibernate 스키마 검증, 전체 게임 스냅샷 및 잔액의 MariaDB 저장, 백엔드 강제 종료 후 복구, 정산 후 DB 재시작과 중복 지급 방지 검증을 통과했습니다.

공개 도메인의 DNS 및 TLS 인증서 발급, 외부 네트워크 접속은 배포 대상이 지정된 후 검증해야 합니다. 로컬 HTTP 검증은 공개 HTTPS 검증을 대체하지 않습니다.

Docker 연결 실패 시 `docker info`, `docker context ls`, `wsl --list --verbose`와 호스트 드라이브 여유 공간을 먼저 확인하세요. 호스트 저장 공간 소진으로 Docker 파일시스템이 읽기 전용으로 전환되었다면 쓰기를 중단하고 공간 확보, 종료된 디스크 백업, Docker 재시작, DB 상태 검증 순서로 복구합니다. 임의의 전체 볼륨 삭제나 초기화는 복구 방법으로 사용하지 마세요.

참고: [Compose 시작 순서](https://docs.docker.com/compose/how-tos/startup-order/), [Compose secrets](https://docs.docker.com/reference/compose-file/secrets/), [Docker WSL 저장소](https://docs.docker.com/desktop/features/wsl/).

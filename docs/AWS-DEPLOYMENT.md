# AWS 배포와 변경 전달

이 문서는 사용자가 직접 AWS를 설정할 때 따르는 절차입니다. 현재 작업에서는 AWS 자원 생성, GitHub 업로드, 이미지 게시 및 원격 명령 실행을 하지 않았습니다.

## 구성

```text
PR / push → GitHub Actions 테스트
수동 릴리스 → 동일 테스트 → GHCR 이미지 두 개 게시
배포 선택 → production 환경 → OIDC → Systems Manager → EC2 release.sh
외부 브라우저 → DuckDNS / HTTPS → Caddy → Spring Boot → MariaDB 볼륨
```

EC2 한 대에 Docker Engine과 Compose를 설치합니다. 릴리스 이미지는 `linux/amd64`로 만들므로 **Ubuntu 24.04 x86_64**를 선택합니다. ARM 계열 인스턴스는 이 워크플로의 대상이 아닙니다. 로컬 빌드의 ARM 지원과 배포 이미지의 아키텍처는 별개입니다.

RAM 4GB 이상을 권장합니다. DB와 backend는 각각 768MiB, web은 256MiB로 제한합니다. 서버에서 소스를 컴파일하지 않습니다. 루트 디스크는 이미지와 DB, 배포 직전 덤프를 보관할 수 있도록 30GB 이상으로 준비하고 사용량을 확인합니다.

별도 RDS, Load Balancer, NAT Gateway 없이 공개 서브넷의 EC2에 80/443을 연결합니다. SSH는 본인 IP만 허용하거나 Session Manager를 사용합니다. DB와 backend 포트는 공개하지 않습니다. 운영은 단일 backend이며 교체 중 짧은 중단이 발생합니다.

## 1. 저장소 준비

GitHub 저장소의 루트에 `.github/`, `Dockerfile`, `compose.release.yml`, `blackjack/`, `frontend/`, `deploy/`가 위치해야 합니다. 현재 폴더의 상위 디렉터리를 저장소 루트로 삼는 경우 워크플로 경로와 빌드 context를 함께 조정해야 합니다.

기본 브랜치는 `main`을 기준으로 합니다. 다른 이름을 사용하면 `release.yml`의 두 branch 조건과 GitHub 환경 제한을 변경하세요. `.env`, `deploy/secrets/`, DB, 덤프, 로컬 캐시를 커밋하지 않습니다.

`ci.yml`은 push와 PR에서 다음을 수행합니다.

1. Java 테스트 및 패키징.
2. 프런트 lint, 타입 검사, 프로덕션 빌드, 런타임 의존성 감사.
3. H2 강제 종료 복구 검증.
4. MariaDB Compose 기동 후 재시작 복구, 게임, 채팅, 인증 통합 검증.
5. 배포 스크립트 구문과 모의 배포·롤백 검증.

`release.yml`은 Actions의 **Build and deliver → Run workflow**로만 시작합니다. 기본 `deploy=false`에서는 테스트와 GHCR 게시까지만 진행합니다. PR 코드로는 이미지 게시와 AWS 배포를 실행하지 않습니다.

## 2. EC2와 네트워크

AWS 계정의 현재 적용 플랜, 제공량과 사용할 서비스를 콘솔에서 먼저 확인합니다. 기존 계정 여부에 따라 적용 조건이 다를 수 있습니다.

1. 사용할 리전에서 Ubuntu 24.04 x86_64 인스턴스를 생성합니다.
2. 공개 서브넷, 공인 IPv4, Internet Gateway 경로를 확인합니다.
3. Security Group에서 TCP 80/443을 외부에 허용합니다.
4. SSH를 사용할 경우 TCP 22의 source를 본인 IP `/32`로 제한합니다.
5. 인스턴스 역할에 `AmazonSSMManagedInstanceCore`를 연결합니다.
6. SSM Agent 설치·실행 상태와 outbound HTTPS를 확인합니다. Systems Manager의 관리 노드 목록에서 Online 상태여야 합니다.
7. [Docker Ubuntu 설치 절차](https://docs.docker.com/engine/install/ubuntu/)에 따라 Engine, Buildx, Compose plugin을 설치합니다.
8. `curl`, `python3`, `util-linux`를 설치합니다. `release.sh`는 Bash와 `flock`을 사용합니다.

인스턴스 역할과 다음 단계의 GitHub 배포 역할은 서로 다릅니다. GitHub 배포 역할은 자원을 생성하는 용도로 사용하지 않습니다.

## 3. DuckDNS와 서버 파일 준비

DuckDNS 주소의 A 레코드를 EC2 공인 IPv4로 설정합니다. IPv6를 구성하지 않았다면 AAAA는 비워 둡니다. 주소에 `https://`를 붙이지 않은 값을 DOMAIN으로 사용합니다.

서버에 다음 파일을 준비합니다.

```text
/opt/blackjack/
  .env
  compose.release.yml
  deploy/
    release.sh
    generate-secrets.py
    secrets/
```

소스에서 `release.sh`, `generate-secrets.py`, `compose.release.yml`을 위 구조로 전송한 뒤, 서버에서 실행합니다.

```bash
sudo mkdir -p /opt/blackjack/deploy
sudo chown -R root:root /opt/blackjack
sudo chmod 700 /opt/blackjack
sudo chmod 755 /opt/blackjack/deploy/release.sh
sudo python3 /opt/blackjack/deploy/generate-secrets.py
sudoedit /opt/blackjack/.env
```

`.env` 예시:

```dotenv
DOMAIN=선택한이름.duckdns.org
IMAGE_PREFIX=ghcr.io/owner/repository
```

`owner/repository`는 소문자 GitHub 저장소 경로입니다. 파일 권한은 `sudo chmod 600 /opt/blackjack/.env`로 제한합니다. 키는 배포 호스트에서 새로 생성하며, 이미 생성한 DB의 비밀번호 파일만 교체하지 않습니다.

GHCR 이미지가 공개 상태이면 로그인 없이 가져올 수 있습니다. 비공개이면 EC2에서 이미지를 읽을 수 있는 최소 범위의 토큰으로 **root 사용자 Docker**에 로그인해야 합니다. 토큰은 명령 인자나 문서에 적지 않고 표준 입력으로 전달합니다. 패키지 공개 여부는 사용자가 GitHub에서 직접 선택합니다.

초기 서버 파일은 사용자가 설치합니다. 워크플로는 서버의 스크립트와 Compose 파일을 자동 덮어쓰지 않습니다. 이 파일들이 변경되는 릴리스는 서버 파일도 검토 후 갱신해야 합니다.

## 4. GitHub OIDC와 권한

GitHub 저장소 Settings → Environments에서 `production`을 만듭니다.

- 배포 가능한 브랜치를 `main`으로 제한합니다.
- 저장소에서 지원하면 required reviewers를 설정합니다.
- 이 환경에 `AWS_REGION`, `AWS_INSTANCE_ID`, `AWS_DEPLOY_ROLE_ARN` 변수를 추가합니다.

IAM에서 GitHub OIDC provider를 등록합니다.

- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

배포 역할의 trust policy는 `deploy/aws-trust-policy.example.json`을 출발점으로 사용합니다. 계정 ID와 OIDC subject를 실제 값으로 바꿉니다. subject는 `production` 환경으로 제한합니다.

기존 subject 형식은 `repo:OWNER/REPOSITORY:environment:production`입니다. 2026-07-15 이후 생성된 저장소 또는 변경된 subject 설정은 owner/repository의 불변 ID가 포함될 수 있습니다. 저장소가 실제로 사용하는 형식에 맞추고 와일드카드로 범위를 넓히지 마세요. [GitHub OIDC 설정](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)

배포 역할 권한은 `deploy/aws-deploy-policy.example.json`의 계정 ID·리전·인스턴스 ID를 바꿔 사용합니다. 지정 인스턴스에 대한 `AWS-RunShellScript` 실행과 결과 조회만 허용합니다. RunShellScript는 대상 서버에서 강한 권한을 가지므로 저장소 관리자, main 변경 권한과 production 환경 접근 권한을 제한합니다.

AWS 장기 access key를 GitHub에 저장하지 않습니다. GitHub hosted runner의 주소를 SSH 허용 목록에 추가할 필요도 없습니다. [Systems Manager Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html)

## 5. 최초 릴리스

1. 검증을 마친 코드를 main에 반영합니다.
2. **Build and deliver**를 `deploy=false`로 실행합니다.
3. 테스트와 두 이미지 게시가 성공하는지 확인합니다.
4. GHCR의 backend/web 패키지 접근 권한을 확인합니다.
5. 해당 커밋의 전체 40자리 SHA를 기록합니다.
6. 첫 기동은 서버에서 아래 명령으로 직접 수행할 수 있습니다.

```bash
sudo /opt/blackjack/deploy/release.sh 전체40자리커밋SHA
```

또는 AWS 설정을 완료한 뒤 동일 workflow를 `deploy=true`로 실행하여 전달 경로까지 확인합니다. 이미지는 `backend:SHA`, `web:SHA`로 게시되며 같은 SHA를 다시 빌드하면 태그가 갱신될 수 있습니다. 재현에 필요한 digest는 Actions 빌드 출력에 함께 보관하세요.

스크립트는 배포 잠금 → 이미지 가져오기 → 기존 DB 덤프 → 컨테이너 교체 → HTTPS healthcheck 순서로 동작합니다. 최초 배포에는 이전 DB 덤프와 롤백 대상이 없습니다. 성공한 SHA는 `.release.env`, 직전 성공 SHA는 `.previous-release.env`에 남깁니다.

HTTPS 검사는 서버의 127.0.0.1로 연결하면서 DOMAIN의 인증서를 검증합니다. 외부 보안 그룹과 DNS 경로의 정상 여부는 별도로 확인해야 합니다.

## 6. 변경 배포와 롤백

수정 PR의 테스트를 확인하고 main에 병합합니다. **Build and deliver**에서 `deploy=true`를 선택하면 같은 테스트를 다시 통과한 후 이미지 게시와 SSM 배포를 실행합니다.

SSM command ID가 Actions 로그에 남습니다. 해당 명령 성공 여부와 브라우저의 실제 화면을 모두 확인합니다. 스크립트는 healthcheck 실패 시 이전 애플리케이션 이미지로 복귀를 시도하고 워크플로를 실패 처리합니다. 배포 상태가 불명확하면 재실행 전에 SSM 결과를 먼저 확인하세요.

이 자동 롤백은 **이전 애플리케이션과 호환되는 DB 스키마 변경에만 사용**합니다. DB dump를 자동 덮어쓰지 않으며, 파괴적인 migration을 되돌려 주지 않습니다. 현재 V1 이후의 migration을 추가할 때는 호환성 및 별도 복원 절차부터 검토해야 합니다.

수동 롤백은 기록된 이전 SHA를 `release.sh` 인자로 넘깁니다. 필요한 이전 이미지를 GHCR에서 삭제하지 마세요. 서버의 배포 파일 변경도 포함된 릴리스는 해당 파일의 버전까지 확인합니다.

## 7. 공개 환경에서 확인할 항목

- 외부 이동통신망에서 HTTPS 접속, 인증서 경고 없음.
- 두 계정의 대기실 채팅, 방 채팅, 이모지 반응.
- 방 외부 사용자의 채팅 구독·전송 거부.
- 로그인 세션 종료 후 메시지 수신 차단.
- 칩 충전 조건과 중복 요청 처리.
- 베팅, 플레이, 정산 후 잔액 일치.
- backend 재시작 후 카드·베팅·잔액 복구와 명시적 이어하기.
- 변경 PR → 테스트 → 이미지 SHA → 배포 → healthcheck의 연결 확인.
- 실제 사용한 화면, 실행 기록, 검증 시각과 커밋 SHA 보관.

채팅은 접속 중 받은 최근 100개를 브라우저 메모리에 표시합니다. 새로고침과 서버 재시작 후 대화 기록을 복원하는 기능은 포함하지 않습니다. 게임·잔액의 영구 저장과 채팅의 일시적 전달은 별도 정책입니다.

## 8. 운영 종료

DB와 필요한 자료를 내려받은 뒤 이번 배포용 EC2를 종료합니다. Stop만으로 모든 자원이 정리되는 것은 아닙니다. 남은 EBS, 스냅샷, 할당한 IP, 이번 작업의 로그 및 기타 자원을 실제 목록에서 확인해 정리합니다. 다른 프로젝트 자원은 유지합니다.

GitHub의 production 배포 변수와 AWS 배포 역할을 비활성화하거나 제거하고 DuckDNS의 오래된 IP 연결도 정리합니다. workflow 실행 권한을 가진 사람이 종료된 환경으로 다시 배포하지 않도록 조치합니다.

## 현재 검증 범위

2026-10-20 로컬 환경에서 다음을 확인했습니다.

- Java 테스트 25개 통과: 게임 규칙, 채팅 검증, 메시지 수신 권한, 동시 칩 충전.
- 프런트 lint·타입 검사·빌드 통과, 런타임 의존성 감사에서 발견된 취약점 0개.
- MariaDB Compose에서 강제 종료 후 카드·베팅·잔액·세션 복구, DB 재시작 후 정산 유지 및 중복 지급 방지.
- 두 사용자 게임 3라운드, 채팅 채널 분리, 사용자 사칭 차단, 퇴장·로그아웃 후 수신 차단, 메시지 길이·반응·전송 빈도 제한.
- 브라우저에서 대기실·게임방 채팅, 이모지, HTML 문자열의 일반 텍스트 표시.
- actionlint와 배포 Compose 구문 검사, 배포 스크립트의 초기 실행·백업·상태 검사 실패 후 롤백·이미지 가져오기 실패·태그 검증.

GitHub hosted runner의 실제 실행, GHCR 게시, AWS OIDC·SSM 권한, 공개 인증서 발급과 EC2의 외부 접속은 사용자가 계정을 연결한 뒤 검증해야 합니다. 로컬 성공을 원격 배포 성공으로 간주하지 않습니다. 위 검증은 전체 보안 취약점이 없음을 보장하는 침투 테스트는 아닙니다.

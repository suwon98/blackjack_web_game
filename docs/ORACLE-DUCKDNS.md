# Oracle Cloud · DuckDNS 배포 절차

확인일: 2026-10-20. 대상은 Ubuntu 24.04 ARM64 서버 한 대이며, Caddy · Spring Boot · MariaDB를 Docker Compose로 실행합니다. 서버를 직접 생성하고 명령을 실행하는 사용자를 위한 절차입니다. 콘솔의 메뉴 이름은 언어와 개편에 따라 달라질 수 있습니다.

## 1. 준비물과 전체 구성

- 이메일, 본인 확인에 사용할 연락처와 카드, MFA 인증 앱
- Windows PowerShell과 기본 OpenSSH의 `ssh`, `scp`, `tar`
- Oracle Cloud 계정과 DuckDNS 로그인 계정
- `blackjack_web_game-main` 소스 폴더

```text
외부 브라우저
  └─ HTTPS / WSS → 선택한이름.duckdns.org:443
                    └─ Oracle VM / Caddy
                         ├─ React 정적 파일
                         └─ backend:8080 → db:3306
                                             └─ MariaDB 영구 볼륨
```

회원과 잔액, 게임 상태는 같은 DB에 저장합니다. 단일 백엔드만 실행하세요. 재시작 시 게임은 일시정지 상태로 복구되며 참가자가 이어하기를 선택합니다. 서버 디스크 손실은 별도 백업 복원이 필요합니다.

아래의 `SERVER_PUBLIC_IP`, `YOUR_PUBLIC_IP`, `선택한이름`은 실제 값으로 바꾸세요. `DOMAIN`에는 `https://`, 경로, 포트를 붙이지 않습니다.

## 2. Oracle Cloud 가입

1. [Oracle Cloud](https://www.oracle.com/cloud/)에서 계정 생성으로 이동합니다.
2. 국가, 이름, 이메일을 입력하고 이메일 인증을 완료합니다.
3. 계정 이름과 암호를 정하고 홈 리전을 선택합니다. 국내 사용자가 중심이면 서울을 먼저 검토하세요. 홈 리전은 가입 후 변경할 수 없으므로 서버 제공 여부와 계정에 표시되는 지역을 확인합니다.
4. 주소, 연락처, 카드 확인을 완료합니다. 카드 확인 과정의 임시 승인 보류는 은행에 따라 해제까지 시간이 걸릴 수 있습니다.
5. 생성된 테넌시로 로그인하고 MFA를 등록합니다. 복구 수단도 안전하게 보관합니다.
6. 계정 업그레이드는 선택하지 않습니다. 체험 크레딧으로만 유지되는 자원을 만들지 말고, 기본 제공 범위와 현재 생성 화면의 할당량을 확인합니다.

이 문서에서 사용할 서버는 `VM.Standard.A1.Flex`, **2 OCPU · RAM 6GB · 부트 디스크 50GB**입니다. 계정 전체의 다른 인스턴스도 합산됩니다. 확인 시점의 A1 월 제공량은 1,500 OCPU 시간과 9,000 GB 시간이며, 31일 동안 위 서버 한 대는 각각 1,488시간과 4,464 GB 시간을 사용합니다.

자리가 없다는 `Out of host capacity` 오류가 나오면 같은 홈 리전의 다른 가용 영역을 확인하거나 나중에 다시 시도하세요. 다른 shape로 임의 변경하지 않습니다. 낮은 사용량의 서버는 회수 대상이 될 수 있으므로 백업을 서버 외부에도 보관합니다.

정책 근거: [Oracle 자원 범위](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm).

## 3. VCN과 서버 생성

### 네트워크

콘솔의 **Networking → Virtual Cloud Networks → VCN Wizard**에서 인터넷 연결이 있는 VCN을 생성합니다. 예시는 다음과 같습니다.

| 항목 | 값 |
|---|---|
| VCN 이름 | `blackjack-vcn` |
| VCN CIDR | `10.0.0.0/16` |
| 공개 서브넷 | `10.0.0.0/24` |
| Internet Gateway | 활성 |
| 공개 서브넷의 경로 | `0.0.0.0/0` → Internet Gateway |
| IPv6 | 이 절차에서는 사용하지 않음 |

마법사가 추가 게이트웨이를 제안하면 공개 서브넷과 Internet Gateway에 필요한 항목만 선택합니다. NAT Gateway, 별도 Load Balancer, 관리형 DB는 이 구성에 필요하지 않습니다.

### 인스턴스

**Compute → Instances → Create instance**에서 다음을 선택합니다.

| 항목 | 설정 |
|---|---|
| 이름 | `blackjack-server` |
| 이미지 | Canonical Ubuntu 24.04, ARM64 / aarch64 |
| Shape | Ampere → `VM.Standard.A1.Flex` |
| CPU / 메모리 | 2 OCPU / 6GB |
| 부트 볼륨 | 50GB, 기본 성능 |
| 네트워크 | 위 VCN의 공개 서브넷 |
| Public IPv4 | 할당 |
| SSH | 새 키 쌍 생성 또는 본인 공개키 등록 |

생성된 **개인키를 다운로드**하여 안전하게 보관합니다. 개인키는 서버나 저장소에 업로드하지 않습니다. 생성 후 상태가 Running인지 확인하고 공인 IPv4를 기록하세요.

### 접근 규칙

공개 서브넷에 연결된 Security List 또는 인스턴스에 연결한 NSG에서 아래의 **stateful ingress** 규칙을 설정합니다. Source port는 All, Destination port에 아래 포트를 넣습니다.

| Source CIDR | 프로토콜 | Destination port | 용도 |
|---|---|---|---|
| `YOUR_PUBLIC_IP/32` | TCP | 22 | 본인 SSH |
| `0.0.0.0/0` | TCP | 80 | 인증서 검증 및 HTTPS 이동 |
| `0.0.0.0/0` | TCP | 443 | 게임 |

기존의 `0.0.0.0/0 → 22` 규칙이 있다면 본인 IP 규칙으로 교체합니다. NSG와 Security List 규칙은 합쳐서 적용되므로 다른 목록에 남은 넓은 허용 규칙도 확인합니다. 응답 및 패키지 다운로드를 위한 egress도 허용되어야 합니다. 3306, 8080, 5173, 18081은 공개하지 않습니다.

공식 화면 흐름: [Linux 인스턴스 생성](https://docs.oracle.com/en-us/iaas/Content/Compute/tutorials/first-linux-instance/overview.htm).

## 4. Windows에서 SSH 접속

PowerShell에서 다운로드한 키의 실제 경로를 사용합니다.

```powershell
ssh -i "C:\Users\Master\Downloads\blackjack-ssh.key" ubuntu@SERVER_PUBLIC_IP
```

첫 연결의 호스트 키 지문을 확인하고 저장합니다. Ubuntu 이미지의 기본 사용자는 `ubuntu`입니다. `Permission denied (publickey)`이면 사용자, 개인키, 인스턴스에 등록된 공개키를 확인하세요. 개인키 권한이 너무 넓다는 오류이면 파일 속성의 보안 설정에서 상속을 끄고 본인 사용자만 읽을 수 있게 조정합니다.

이후 별도 표시가 없는 명령은 **서버의 SSH 터미널**에서 실행합니다.

```bash
uname -m
dpkg --print-architecture
df -h /
sudo apt-get update
sudo apt-get install -y ca-certificates curl python3 dnsutils
```

예상 아키텍처는 각각 `aarch64`, `arm64`입니다. 다르게 나오면 이미지와 shape를 다시 확인합니다.

## 5. Docker Engine과 Compose 설치

새 Ubuntu 서버 기준입니다. 이미 Docker 패키지가 설치되어 있다면 [공식 설치 문서](https://docs.docker.com/engine/install/ubuntu/)의 충돌 패키지 항목부터 확인합니다.

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "$VERSION_CODENAME")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker info --format '{{.Architecture}}'
sudo docker compose version
```

이 문서는 Docker 명령에 `sudo`를 사용합니다. Docker Desktop은 서버에 설치할 필요가 없습니다. Docker가 공개하는 컨테이너 포트는 UFW 규칙을 우회할 수 있으므로, 외부 접근 범위는 OCI 규칙에서도 제한합니다.

서버 방화벽은 아래 명령으로 먼저 살펴봅니다. `ufw: command not found`이면 UFW가 설치되지 않은 상태이므로 iptables 출력을 확인합니다.

```bash
sudo iptables -S INPUT
sudo iptables -S FORWARD
sudo ufw status
```

UFW가 활성인 서버라면 `sudo ufw allow 80/tcp`, `sudo ufw allow 443/tcp`를 적용합니다. UFW가 비활성이고 OCI 기본 iptables의 INPUT REJECT가 있는 경우에만 다음을 적용합니다.

```bash
sudo iptables -C INPUT -p tcp -m multiport --dports 80,443 -j ACCEPT || sudo iptables -I INPUT 1 -p tcp -m multiport --dports 80,443 -j ACCEPT
```

`netfilter-persistent`가 설치된 서버라면 `sudo netfilter-persistent save`로 저장합니다. 없다면 `sudo apt-get install iptables-persistent`를 실행하고 현재 규칙 저장 질문을 확인한 후 저장합니다. UFW와 이 방식을 함께 관리하지 마세요. 기존 규칙 전체를 비우거나 SSH 창을 닫은 상태에서 방화벽을 바꾸지 마세요. Docker의 FORWARD/DOCKER 체인은 임의 수정하지 않습니다.

## 6. 소스 업로드

**로컬 Windows PowerShell**에서 실행합니다. 압축 파일은 프로젝트 상위 폴더에 만듭니다.

```powershell
cd C:\Users\Master\blackjack\blackjack_web_game-main
tar --exclude="*/node_modules" --exclude="*/build" --exclude="*/.gradle" --exclude="*/dist" --exclude="*/data" --exclude="*/.env*" --exclude="*/.git" -czf ..\blackjack-deploy.tar.gz Dockerfile compose.yml .dockerignore .gitattributes deploy/Caddyfile deploy/generate-secrets.py blackjack frontend
tar -tzf ..\blackjack-deploy.tar.gz | Select-Object -First 20
scp -i "C:\Users\Master\Downloads\blackjack-ssh.key" ..\blackjack-deploy.tar.gz ubuntu@SERVER_PUBLIC_IP:/home/ubuntu/
```

로컬 비밀키, DB 파일, `node_modules`, 빌드 캐시를 옮기지 않는 구성입니다. 이미 공개 운영 중인 서버의 업데이트에는 기존 디렉터리에 바로 덮어쓰지 말고 먼저 백업과 별도 릴리스 디렉터리를 준비하세요.

다시 **서버 SSH**에서 새 디렉터리를 만들고 압축을 풉니다.

```bash
mkdir -p /home/ubuntu/blackjack
chmod 700 /home/ubuntu/blackjack
tar -xzf /home/ubuntu/blackjack-deploy.tar.gz -C /home/ubuntu/blackjack
cd /home/ubuntu/blackjack
ls Dockerfile compose.yml deploy/generate-secrets.py
```

이후 Compose 명령은 항상 이 디렉터리에서 실행합니다. 디렉터리 이름이 바뀌어도 볼륨 연결이 바뀌지 않도록 아래 단계에서 프로젝트 이름을 고정합니다.

## 7. DuckDNS 가입과 DNS 설정

1. [DuckDNS](https://www.duckdns.org/)에 접속합니다.
2. 현재 화면에 제공되는 로그인 공급자 중 본인이 사용하는 계정으로 로그인합니다.
3. Domains 입력에 원하는 이름을 넣고 추가합니다. 이미 사용 중인 이름이면 다른 이름을 선택합니다.
4. 해당 도메인의 Current IP를 **Oracle 서버의 공인 IPv4**로 바꾸고 Update IP를 누릅니다. 자동 입력된 집이나 회사 IP를 그대로 두지 마세요.
5. IPv6는 비워 둡니다. Token은 계정 제어용 비밀값이므로 공유하지 않습니다. 수동 IPv4 지정과 Caddy의 기본 인증서 발급에는 Token이 필요 없습니다.

서버에서 확인합니다.

```bash
dig +short 선택한이름.duckdns.org A
dig +short 선택한이름.duckdns.org AAAA
```

A 결과가 서버 공인 IPv4와 같고 AAAA는 비어 있어야 합니다. DNS 반영에는 시간이 걸릴 수 있습니다. 서버를 재생성하거나 공인 IP가 바뀌면 DuckDNS 값도 갱신합니다. 자동 갱신이 필요하면 [DuckDNS 설치 안내](https://www.duckdns.org/install.jsp)를 따르고 토큰을 저장소에 넣지 마세요.

## 8. 키 생성과 컨테이너 실행

서버의 프로젝트 루트에서 실행합니다.

```bash
cd /home/ubuntu/blackjack
python3 deploy/generate-secrets.py
printf 'DOMAIN=선택한이름.duckdns.org\nCOMPOSE_PROJECT_NAME=blackjack\n' > .env
chmod 600 .env
sudo docker compose config --quiet
sudo docker compose pull db
sudo docker compose build --pull backend
sudo docker compose build --pull web
sudo docker compose up -d
sudo docker compose ps
sudo docker compose logs --tail=100 backend web
```

`DOMAIN`은 실제 등록한 주소로 바꿉니다. 빌드를 순서대로 실행하면 메모리 사용량이 겹치는 것을 줄일 수 있습니다. DB 초기화와 Java 기동, 인증서 발급이 끝날 때까지 기다립니다.

이 절차에는 `compose.local.yml`을 사용하지 않습니다. 로컬용 파일은 HTTP 및 loopback 포트를 설정하므로 공개 배포 구성과 다릅니다.

기대 상태는 DB healthy, backend와 web running입니다. 로그에서 Flyway 마이그레이션 성공과 Spring Boot 기동을 확인합니다. 비밀키를 다시 생성하거나 DB 볼륨을 지워서 기동 문제를 해결하려고 하지 마세요.

## 9. HTTPS와 외부 플레이 확인

```bash
curl -I http://선택한이름.duckdns.org
curl --fail https://선택한이름.duckdns.org/actuator/health
```

첫 요청은 HTTPS로 이동하는 응답, 두 번째는 HTTP 200과 `UP` 상태가 기대값입니다. `curl -k`로 인증서 검증을 건너뛰지 않습니다. Caddy는 80/443과 올바른 DNS, 영구 `/data`가 준비되면 인증서를 발급하고 갱신합니다. [Caddy 동작 조건](https://caddyserver.com/docs/automatic-https)

휴대전화 Wi-Fi를 끄고 이동통신망으로 아래 항목을 확인합니다.

1. `https://선택한이름.duckdns.org`에 인증서 경고 없이 접속한다.
2. 회원가입과 로그인이 된다.
3. 서로 다른 두 브라우저 프로필로 방 입장, 착석, 베팅, 정산이 된다.
4. 개발자 도구 Network의 WebSocket 연결이 `wss://`이고 연결 상태가 유지된다.
5. 베팅 후 서버에서 `sudo docker compose restart backend`를 실행한다.
6. 같은 계정으로 돌아오면 진행 중 게임이 일시정지되어 있고 카드, 베팅, 잔액이 유지된다.
7. 이어하기 후 정산이 한 번만 반영된다.
8. 정산 후 `sudo reboot`로 VM을 재시작하고 SSH 재접속 후 `sudo docker compose ps`와 HTTPS 접속을 확인한다.

운영 중 다른 사용자가 없는 시간에 재시작 검증을 하세요. DB와 Caddy 저장 볼륨은 재시작 후에도 남아야 합니다.

## 10. 백업과 업데이트

매일 및 업데이트 직전에 DB 덤프를 만듭니다. 프로젝트 루트에서 실행하세요.

```bash
mkdir -p backups
chmod 700 backups
umask 077
backup_file="backups/blackjack-$(date +%Y%m%d-%H%M%S).sql"
sudo docker compose exec -T db sh -c 'MYSQL_PWD="$(cat /run/secrets/db-root-password)" mariadb-dump -uroot --single-transaction --routines --triggers blackjack' > "$backup_file"
test -s "$backup_file"
```

명령 실패 여부와 파일 크기를 확인합니다. DB 덤프와 `deploy/secrets/`를 암호화해 개인 보관 장소에도 복사합니다. 같은 VM에만 있는 백업은 서버 손실을 대비하지 못합니다. 백업에는 숨겨진 카드와 계정 데이터가 있으므로 공개 저장소에 올리지 마세요.

업데이트 전에 실행 이미지 ID도 기록하고, 이전 소스와 비밀키를 보관합니다.

```bash
sudo docker compose images
df -h /
sudo docker system df
```

새 소스를 적용한 뒤 `sudo docker compose build --pull backend`, `sudo docker compose build --pull web`, `sudo docker compose up -d`를 실행하고 9단계를 다시 확인합니다. 스키마 변경이 있는 버전은 이전 이미지로만 되돌려서는 안 되며 DB 복원 계획도 필요합니다.

서비스 정지는 `sudo docker compose stop`, 재개는 `sudo docker compose up -d`입니다. `down -v` 및 볼륨 정리 명령은 데이터 삭제를 수반하므로 일상 운영에 사용하지 않습니다. 키 파일만 교체해도 이미 초기화한 DB의 암호가 바뀌지는 않습니다.

OS 보안 업데이트, 디스크 잔여 공간, 컨테이너 재시작 횟수, 인증서 만료 및 HTTPS 응답을 정기 확인합니다. 낮은 사용량에 따른 자원 회수 가능성에 대비해 실제 백업 복원도 별도 환경에서 확인하세요.

## 11. ARM64 사전 점검

Oracle 실기기에 대한 배포 검증은 아직 수행하지 않았습니다. 로컬 x86_64 Docker에서 ARM64 에뮬레이션 검증을 수행했으며, 결과는 아래에 기록합니다. 실제 ARM 서버의 처리 속도와 외부 HTTPS 인증서 발급, 전체 게임 복구는 9단계에서 확인해야 합니다.

| 항목 | 검토 내용 |
|---|---|
| 기반 이미지 | Temurin 17 JDK/JRE, Node 22 Alpine, Caddy 2 Alpine, MariaDB 11.4의 linux/arm64 manifest 모두 확인 |
| Java | Java 17 바이트코드, MariaDB JDBC 사용. 프로젝트에 직접 포함한 아키텍처 전용 JNI 라이브러리 없음 |
| 프런트 | 잠금 파일에 Rolldown Linux ARM64 musl 패키지 포함. Alpine 컨테이너 안에서 `npm ci` 실행 |
| Gradle 캐시 | Dockerfile의 캐시 ID를 TARGETARCH별로 분리 |
| Windows 줄바꿈 | `.gitattributes`에 LF 지정, Docker 빌드에서도 gradlew의 CRLF 정규화 |
| 이미지 선택 | 배포 VM에서 직접 빌드하여 ARM64를 선택. Windows amd64 이미지를 그대로 복사하지 않음 |
| 메모리 | DB/백엔드/web 한도 합계 1,792MiB. 빌드는 이 한도와 별개이므로 순차 수행 |

배포 서버에서 실제 이미지 아키텍처를 확인합니다.

```bash
for service in db backend web; do
  image_id=$(sudo docker compose images -q "$service")
  sudo docker image inspect "$image_id" --format '{{.Os}}/{{.Architecture}}'
done
```

모두 `linux/arm64`가 기대값입니다. `DOCKER_DEFAULT_PLATFORM=linux/amd64` 또는 Compose의 `platform: linux/amd64`를 설정하지 마세요.

### 로컬 확인 결과

2026-10-20 x86_64 Docker Desktop의 ARM64 에뮬레이션에서 확인했습니다.

- `web` 대상 ARM64 이미지 빌드 성공: 컨테이너 내부 `npm ci`, TypeScript 검사, Vite 프로덕션 빌드 완료.
- `backend` 대상 ARM64 이미지 빌드 성공: ARM64 Temurin JDK에서 Gradle 9.4.1의 Java 컴파일과 `bootJar` 완료. 첫 Gradle 실행은 에뮬레이션에서 약 9분 29초 소요되었습니다. 이는 Oracle 실기기의 예상 빌드 시간이 아닙니다. 이 Docker 빌드는 단위 테스트 실행을 포함하지 않습니다.
- ARM64 Caddy 실행 성공: 2.11.7.
- ARM64 Caddy의 운영 Caddyfile 설정 검증 성공.
- ARM64 Java 실행 성공: Temurin 17.0.20.1.
- ARM64 MariaDB 11.4.13 초기화 및 `healthcheck.sh --connect --innodb_initialized` 통과. 기존 서비스와 연결하지 않은 tmpfs 기반 임시 DB를 사용한 뒤 정리했습니다. 초기 관찰 시간 안에는 준비되지 않았으나 이후 정상 연결되었습니다.
- 에뮬레이션 DB는 io_uring/native AIO 미지원 경고 후 대체 방식으로 기동했습니다. Oracle 실기기에서 동일 경고가 발생한다고 단정하지 않으며, 실제 서버의 로그와 DB healthcheck로 판정합니다.
- 배포 압축 생성 성공: 188개 항목. 비밀키, 로컬 DB, node_modules, Gradle 캐시 제외 확인.
- 문서의 Bash 예제 12개 구문 검사 통과.

기반 이미지 태그는 변경될 수 있습니다. 다음은 조회 당시의 이미지 인덱스 digest입니다.

| 이미지 | 인덱스 digest |
|---|---|
| eclipse-temurin:17-jdk | `sha256:fe947d993c32dd9cf651a4353a6ed723c2370b3a9d034ea4cab4775404ea531a` |
| eclipse-temurin:17-jre | `sha256:e04fa72be13b257a59fdec6dc14af78c8f80f035d3c1ed4e03e45552a231d4c8` |
| node:22-alpine | `sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402` |
| caddy:2-alpine | `sha256:d8542f48d34a9cf4e4c11a478865229840e87e4c96ea3f439101f31a5d35f75f` |
| mariadb:11.4 | `sha256:250e1609e93c0e6aecf64bfbab2d84d5e89d2481540fe77447ff6c855c3b1873` |

## 12. 문제별 확인 순서

| 증상 | 확인 순서 |
|---|---|
| SSH timeout | 공인 IP → 공개 서브넷 → Internet Gateway 경로 → 22의 source CIDR → 서버 방화벽 |
| HTTPS timeout | DNS A → OCI 80/443 규칙 → 서버 방화벽 → web 컨테이너와 포트 |
| 인증서 발급 실패 | A/AAAA 불일치, 외부 80/443 도달 여부, 서버 시각, Caddy 로그. 반복 재발급 전에 원인 수정 |
| 502 응답 | backend 로그 → DB healthy → Flyway/비밀키 → 컨테이너 메모리 |
| 로그인 또는 WS 실패 | 주소와 DOMAIN 일치 → HTTPS 사용 → 브라우저 Origin → backend 로그 |
| `exec format error` | 이미지 아키텍처 확인 후 ARM 서버에서 다시 빌드 |
| `gradlew: not found` 또는 `^M` | LF 줄바꿈과 Dockerfile의 정규화 단계 확인 |
| ARM 네이티브 모듈 누락 | Windows node_modules 복사 여부, npm optional 의존성 제외 설정 여부 확인. 컨테이너 안에서 npm ci 재실행 |
| Java 종료 / exit 137 | `sudo docker stats --no-stream`, OOM 여부, 빌드 동시 실행 여부 확인 |
| 재시작 후 데이터가 없음 | Compose 프로젝트 이름과 볼륨 연결 확인. 새 볼륨 초기화 또는 기존 볼륨 삭제 금지 |
| 다른 서버가 DB를 사용한다는 오류 | 동일 DB에 연결된 두 번째 backend가 있는지 확인 |
| 재부팅 후 접근 불가 | Docker enable 상태, 컨테이너 상태, IP/DNS 변경, 방화벽 영구 저장 여부 확인 |

조회 명령:

```bash
sudo docker compose ps
sudo docker compose logs --tail=150 db backend web
sudo docker stats --no-stream
sudo ss -lntp
```

로그를 공유할 때는 토큰, 암호, 개인 정보와 DB 덤프를 제외합니다.

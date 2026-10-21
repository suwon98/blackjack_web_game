# GitHub Actions 첫 변경 배포

## 준비한 변경

브랜치: fix/max-bet-chip-step.
MAX와 칩 추가가 100칩 단위와 서버 상한을 지키도록 수정한다.
현재 로컬 수정이며 커밋, push, PR, 실제 배포는 사용자가 수행한다.

## 1. EC2의 Systems Manager 연결 확인

EC2 SSH에서 실행한다.

```bash
sudo systemctl status amazon-ssm-agent --no-pager
sudo systemctl status snap.amazon-ssm-agent.amazon-ssm-agent.service --no-pager
```

설치 방식에 따라 한쪽만 존재할 수 있다. 하나가 active이면 된다.
두 방식으로 중복 설치하지 않는다.
Ubuntu 26.04 서버의 실제 설치 상태를 먼저 확인하고 설치 필요 여부를 판단한다.

AWS 콘솔에서 서울 리전의 해당 EC2에 연결된 IAM 역할에 AmazonSSMManagedInstanceCore가 있는지 확인한다.
Systems Manager의 관리형 노드/Fleet Manager에서 해당 인스턴스가 Online인지 확인한다.
EC2의 인스턴스 ID(i-로 시작)를 기록한다.
Agent가 active인데 목록에 없다면 IAM 역할, 리전, 아웃바운드 HTTPS와 DNS를 확인한다. 인바운드 포트 추가는 필요하지 않다.

참고: [AWS 인스턴스 권한](https://docs.aws.amazon.com/systems-manager/latest/userguide/setup-instance-permissions.html),
[AWS Ubuntu Agent](https://docs.aws.amazon.com/systems-manager/latest/userguide/agent-install-ubuntu-64-snap.html).

## 2. GitHub production 환경 생성

저장소 Settings → Environments → New environment에서 production 생성.
배포 브랜치는 main만 허용한다. 지원되는 경우 승인자 설정을 적용한다.
Environment variables에 다음을 넣는다.

| 이름 | 값 |
| --- | --- |
| AWS_REGION | ap-northeast-2 |
| AWS_INSTANCE_ID | 실제 EC2 인스턴스 ID |
| AWS_DEPLOY_ROLE_ARN | 4단계에서 만드는 역할 ARN |

EC2의 SSH 개인키나 AWS 장기 access key는 저장하지 않는다.
이 값들은 Repository secrets 대신 production의 Variables로 설정한다. 현재 workflow는 vars를 참조한다.

## 3. AWS OIDC 공급자 등록

IAM → Identity providers → Add provider.
이미 같은 공급자가 있다면 재사용한다.

- 유형: OpenID Connect
- URL: https://token.actions.githubusercontent.com
- Audience: sts.amazonaws.com

## 4. GitHub 배포 역할 생성

EC2에 붙인 역할과 별개의 역할 blackjack-github-deploy를 만든다.
Custom trust policy에는 deploy/aws-trust-policy.example.json을 사용한다.

- ACCOUNT_ID를 실제 AWS 계정 ID로 치환.
- REPLACE_WITH_EXACT_PRODUCTION_SUBJECT를 실제 저장소의 production subject로 치환.

이 저장소는 공개 API 기준 2026-05-13 생성됐다.
기본 형식을 유지한 경우 subject:

```text
repo:suwon98/blackjack_web_game:environment:production
```

불변 subject에 가입했거나 변경 정책 적용을 받은 경우 정확한 형식:

```text
repo:suwon98@250905326/blackjack_web_game@1237221129:environment:production
```

생성일만으로 현재 subject 설정을 확정할 수 없다. 설정 상태와 일치하는 하나를 사용한다.
AssumeRoleWithWebIdentity 오류가 나면 subject 및 environment 이름부터 확인하고 와일드카드로 넓히지 않는다.

역할의 Permissions → Add permissions → Create inline policy에서 deploy/aws-deploy-policy.example.json을 사용한다.
REGION은 ap-northeast-2, ACCOUNT_ID와 INSTANCE_ID는 실제 값으로 치환한다.
이 역할은 지정 EC2에 AWS-RunShellScript 실행과 명령 결과 조회만 허용한다.
AdministratorAccess를 추가하지 않는다.

생성한 역할 ARN을 2단계의 AWS_DEPLOY_ROLE_ARN에 입력한다.

참고: [GitHub AWS OIDC](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws).

## 5. 로컬 변경 검토와 push

Windows PowerShell에서 실행한다.

```powershell
cd C:\Users\Master\blackjack-github
git branch --show-current
git status --short
git diff --check
git diff
```

브랜치는 fix/max-bet-chip-step이어야 한다.
수정 파일과 새 테스트/문서만 확인하고 아래 파일을 명시적으로 추가한다.

```powershell
git add .github/workflows/ci.yml frontend/frontend-blackjack/package.json frontend/frontend-blackjack/src/pages/GamePage.tsx frontend/frontend-blackjack/src/utils/betting.ts frontend/frontend-blackjack/scripts/betting.test.mjs docs/PLAYTEST-2026-10-22.md docs/FIRST-ACTIONS-DEPLOYMENT.md
git diff --cached --stat
git commit -m "Fix maximum bets for chip remainders"
git push -u origin fix/max-bet-chip-step
```

PR의 base는 main, compare는 fix/max-bet-chip-step.

제목: Fix maximum bets for chip remainders

설명:
> 잔액이 96,850칩일 때 MAX가 서버에서 허용하지 않는 96,850을 선택해 베팅을 확정할 수 없었습니다. MAX와 칩 추가의 상한을 100칩 단위로 맞춰 96,800을 선택하며 남은 50칩은 지갑에 유지합니다.
>
> 단위 안내를 추가하고 7개 회귀 테스트를 Verify blackjack에 연결했습니다. 로컬에서 npm ci, npm test, lint, build를 확인했습니다. 방장/준비 상태 변경은 포함하지 않습니다.

Verify blackjack의 현재 PR 커밋 검사가 통과한 뒤 merge한다.

## 6. 실제 Actions 배포

테스트 사용자를 게임 방에서 퇴장시키고 배포 전 각 계정 잔액을 기록한다.
현재 workflow와 컨테이너 구성은 무중단 교체를 보장하지 않는다.

GitHub Actions → Build and deliver → Run workflow:
- Branch: main
- deploy: true

이번에는 서버에서 release.sh를 수동으로 실행하지 않는다. Actions가 전달해야 CD 경로를 검증할 수 있다.

성공 기준:
1. verify / test 성공
2. images 성공: backend 및 web 이미지 태그가 이번 main의 SHA
3. deploy 성공: OIDC 역할 획득 및 SSM 명령 성공
4. deploy 로그의 SSM command ID 확인
5. EC2 .release.env의 SHA가 이번 실행의 SHA와 같음
6. 외부 HTTPS와 변경 화면 확인

SSM 명령이 실패하면 같은 배포를 반복 실행하기 전에 명령 결과와 서버 로그를 먼저 확인한다.

## 7. 배포 버전과 실제 변경 확인

EC2에서 실행한다.

```bash
sudo cat /opt/blackjack/.release.env
sudo docker compose --env-file /opt/blackjack/.env --env-file /opt/blackjack/.release.env -f /opt/blackjack/compose.release.yml ps
curl --fail --show-error https://blackjack-sw.duckdns.org/actuator/health
```

브라우저 새로고침 후:
- 베팅 화면에 100칩 단위 안내가 표시된다.
- 잔액 끝자리가 50인 테스트 계정에서 MAX가 100칩 단위로 내려간다.
- 예: 95,850 잔액에서 MAX → 95,800 선택, 베팅 확정 버튼 활성화.
- MAX 선택만으로 잔액이 차감되지 않는다. 실제 베팅 확정은 별도로 선택한다.
- 배포 전 기록한 잔액과 게임 복구 상태를 확인한다.
- 두 계정 채팅을 확인한다.

## 8. 보관할 검증 기록

PR 링크, 변경 SHA, Verify blackjack 실행 링크, Build and deliver 실행 링크,
SSM command ID, 서버 .release.env 값, HTTPS 상태, 변경 화면 캡처를 같은 변경 기준으로 보관한다.
JWT, GHCR 토큰, SSH 키, DB 암호는 기록하지 않는다.

첫 변경 배포를 확인한 다음 방장과 준비 상태 정책을 별도 브랜치에서 진행한다.

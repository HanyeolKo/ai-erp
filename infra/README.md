# 개발 인프라

PostgreSQL 18.6과 Redis 8.2.9만 제공하는 로컬 개발용 Compose 구성입니다.

```sh
docker compose --env-file infra/.env.example -f infra/compose.dev.yml up -d
docker compose -f infra/compose.dev.yml logs -f
docker compose -f infra/compose.dev.yml stop
```

`stop`은 named volume을 유지합니다. 초기화는 아래 명령이며 PostgreSQL·Redis의 모든 개발 데이터가 삭제됩니다.

```sh
docker compose -f infra/compose.dev.yml down -v
```

## 운영 배포

운영 서버는 `deploy@192.168.219.100`이고 checkout은 `/home/deploy/actions-runner-ai-erp/_work/ai-erp/ai-erp`이다. Docker 29.4.0, Compose 5.1.2, Bash, Python 3, Git, util-linux의 `flock`과 `ss`, GNU coreutils, curl을 설치한다. 여유 disk 20 GiB와 available memory 2.5 GiB 이상이 필요하다.

최초 host 준비는 deploy 사용자로 실행한다. 기존 경로가 있으면 먼저 소유자, 권한, symbolic link 여부를 확인한다. 아래 명령은 신규 host에만 적용한다.

```sh
umask 077
mkdir -m 700 /home/deploy/ai-erp
mkdir -m 700 /home/deploy/ai-erp/shared /home/deploy/ai-erp/releases
mkdir -m 700 /home/deploy/ai-erp/shared/caddy
install -m 600 infra/.env.prod.example /home/deploy/ai-erp/shared/.env
```

서버의 `.env`를 직접 편집해 긴 임의 secret을 넣는다. `.env`는 shell script가 아니며 `KEY=literal` 형식이다. shell substitution, 중복·알 수 없는 key, 공백이 있는 값은 거부된다. `POSTGRES_DB=ai_erp`, 일치하는 PostgreSQL/DB credential, `DB_URL=jdbc:postgresql://postgres:5432/ai_erp`, URL-safe Redis password와 정확히 일치하는 `redis://:<password>@redis:6379/0`, 기본 도메인인 `SITE_ADDRESS=ai-erp.duckdns.org`를 사용한다. `SITE_ADDRESS`는 `ai-erp.duckdns.org`, `blackcow.duckdns.org`, 기존 IP인 `192.168.219.100`만 허용한다. Google credential은 두 값이 모두 비어 있거나 모두 있어야 하며 `APP_OIDC_ENABLED=true`이면 둘 다 필요하다. `APP_IMAGE`는 운영 env에 넣지 않는다. 배포 script가 commit SHA에서 직접 결정한다.

Google 로그인은 서버의 `APP_OIDC_ENABLED=true`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`으로 활성화한다. 비밀은 서버 `.env`에만 보관한다. Google OAuth client의 승인된 redirect URI에 `https://ai-erp.duckdns.org/login/oauth2/code/google`과 `https://blackcow.duckdns.org/login/oauth2/code/google`을 모두 등록한다. Caddy는 두 도메인과 기존 IP를 함께 처리한다. IP의 `/`와 `/index.html` 진입은 앱이 실행되기 전에 기본 도메인으로 이동하여 초대 정보와 세션이 같은 도메인에 저장되게 한다. redirect의 Location에는 fragment를 넣지 않으므로 브라우저가 초대 링크의 `#/invitations/...`를 그대로 이어받는다. IP의 `/oauth2/authorization/google` 요청도 기본 도메인으로 이동한다. IP의 health·API·문서는 계속 직접 제공하며, 각 도메인에서 시작한 로그인은 해당 도메인의 callback을 사용한다.

HTTP 80번 포트로 접속하면 요청 경로와 query를 유지하여 `https://ai-erp.duckdns.org`로 HTTP 308 redirect한다. IP로 입력한 HTTP 주소도 같은 기본 도메인으로 이동한다. 목적지에는 요청의 Host를 사용하지 않으며 HTTPS의 허용 주소나 인증서 범위는 늘어나지 않는다.

아래 명령을 정확한 clean checkout에서 실행한다. tracked 변경과 nonignored untracked 파일이 모두 없어야 한다. 운영에서 `AI_ERP_TEST_MODE`와 경로 override를 설정하지 않는다.

```sh
release=$(git rev-parse HEAD)
bash scripts/preflight.sh "$release"
bash scripts/deploy.sh "$release"
bash scripts/smoke.sh public "$release"
```

배포는 host 경로 검증과 실제 lock 획득 뒤 project-owned volume/network 및 80/443 publisher를 검사한다. 같은 이름의 legacy 또는 label 없는 resource가 있으면 중단한다. 기존 서비스의 중지는 별도 승인과 ID 확인 절차로만 수행한다. 이 script는 비프로젝트 container를 중지하거나 volume/image를 제거하지 않는다.

배포 순서는 immutable image build → PostgreSQL/Redis/Caddy health → `pg_dump -Fc`와 `pg_restore -l` 검증 → source 재검증 → external Flyway 한 번 → inactive app health·image 검증·internal smoke → 전체 Caddy candidate 검증·reload → public smoke·20초 관찰·public smoke → manifest와 state 원자 기록 → 이전 app 중지이다. 최초 빈 DB에도 migration 직전 backup을 만든다. app의 Spring Flyway는 비활성화한다.

Caddy는 checkout의 `infra` 디렉터리를 `/etc/caddy/source`에 read-only로 연결하고 시작과 reload 모두 `/etc/caddy/source/Caddyfile`을 읽는다. checkout이 파일을 교체해도 현재 파일을 읽도록 단일 파일 bind mount를 사용하지 않는다. Candidate 검증과 manifest·backup의 Caddy checksum도 같은 checkout 파일을 기준으로 한다. 기존 단일 파일 mount에서 전환할 때에는 다음 배포의 Compose `up`이 변경된 Caddy 서비스 설정을 적용한다.

`shared/active-state.json`은 현재 release/color/image와 직전 release/color를 기록한다. `shared/caddy/active-upstream.caddy`와 manifest, local image 및 running container가 모두 일치해야 다음 배포가 가능하다. 최초 상태는 state가 없고 app이 실행되지 않는 503 initialization이다. 같은 active SHA의 재실행은 public smoke만 수행하고 완료된 비활성 SHA의 재배포는 거부한다. `.env` 변경은 실행 중인 app에 자동 적용되지 않으므로 새 source SHA 배포로 활성화한다. Smoke는 서버 설정과 app의 로그인 준비 상태가 다르면 실패한다.

Rollback은 보존된 immutable image를 현재 inactive slot에서 검증하고 같은 Caddy/public-smoke transaction으로 전환한다. migration을 역실행하지 않으며 원래 manifest를 수정하지 않는다. 연속해서 인자 없이 실행하면 직전 active release로 다시 이동한다.

```sh
bash scripts/rollback.sh
bash scripts/rollback.sh <preserved-40-character-sha>
bash scripts/backup.sh <40-character-sha>
```

Release별 `manifest.json`과 checksum, `events.log`, `backups/postgres-<timestamp>-<pid>.dump` 및 같은 이름의 `.json` metadata를 mode 600으로 보존한다. 디렉터리는 mode 700이다. Backup metadata에는 dump·Compose·Caddy·migration·active-state checksum과 Flyway history 존재 여부가 들어간다. `BACKUP_RETENTION_DAYS`는 0–9999 범위의 정수이며 기본값은 14일이다. 보존 기간이 지난 해당 backup 파일만 제거한다.

Caddy validation/reload, public smoke, manifest/state 기록이 실패하면 이전 upstream과 state의 정확한 내용을 복원하고 Caddy를 reload한다. 첫 배포 실패는 503와 state 부재로 복원되어 재시도할 수 있다. 복구 자체가 실패하면 exit 2를 반환하고 transaction 파일을 보존한다. 이 경우 자동 재배포를 멈추고 `*.previous`, 현재 upstream/state, running container를 확인해 운영자가 복구한다. 프로세스 강제 종료나 전원 장애 후 남은 transaction 파일도 같은 방식으로 확인한다. 비밀이 포함되는 Compose 전체 config나 container environment를 로그에 출력하지 않는다.

Production image는 OpenAPI → typed client·Swagger → frontend → Spring Boot jar 순서로 생성하고, `/`, API, `/assets/api-docs/index.html`을 같은 release에서 제공한다. Caddy만 80/443을 공개하고 세 주소에 기존 내부 TLS를 사용한다. IP의 SNI 없는 접속도 지원한다. 이번 구성은 공인 인증서나 ACME 발급을 수행하지 않으므로 브라우저 신뢰가 필요하면 Caddy root CA를 별도 배포한다. Public smoke는 `--resolve <SITE_ADDRESS>:443:192.168.219.100 --noproxy '*' --insecure`로 LAN IP에 직접 연결하면서 HTTP Host와 TLS SNI는 선택한 주소로 유지한다. 따라서 서버 검사는 public DNS의 hairpin NAT에 의존하지 않는다. 브라우저를 사용하는 LAN 장치에서도 각 도메인이 접근 가능한 서버 주소로 해석되어야 한다.

Smoke는 일반 endpoint의 HTTP 200과 public release header, readiness `UP`을 검사한다. `SITE_ADDRESS`가 IP이면 `/`의 정확한 기본 도메인 redirect를 먼저 확인한 뒤 기본 도메인의 `/`에서 HTTP 200을 확인한다. 두 요청 모두 고정된 LAN IP로 연결한다. `APP_OIDC_ENABLED=true`이면 configuration의 `login=READY`와 정확한 `loginUrl=/oauth2/authorization/google`을 요구하고, `false`이면 `CONFIGURATION_REQUIRED`와 null URL을 요구한다. 익명 `/api/v1/me`는 HTTP 401이어야 한다. 로그인 활성화 시 public smoke는 Google redirect의 대상과 현재 도메인의 callback을 검사하며 Google redirect를 따라가거나 client ID, cookie, state, Location을 출력하지 않는다.

검증은 다음과 같다. 첫 명령은 예시 env에 image key가 없으므로 검사할 image reference를 별도로 제공한다.

```sh
APP_IMAGE=ai-erp:config-check docker compose --env-file infra/.env.prod.example -f infra/compose.prod.yml config --quiet
bash scripts/tests/deployment-contract.sh
bash scripts/tests/caddy-no-sni.sh
docker build --label "org.opencontainers.image.revision=$(git rev-parse HEAD)" --tag "ai-erp:$(git rev-parse HEAD)" .
```

Contract harness는 실제 shell 진입점을 strict stateful host double로 실행한다. Linux에서 실제 `flock` 경쟁과 POSIX 권한·symlink를 검사한다. `caddy-no-sni.sh`는 임시 Docker Caddy에서 두 도메인, IP의 SNI 유무, IP의 앱 진입·로그인 시작 redirect와 직접 제공하는 health·문서, HTTP 80번 포트의 고정 HTTPS 목적지를 검증한다. Windows Git Bash 검증은 해당 Linux 기능과 실제 Docker 실행을 대체하지 못하며 이를 명시적으로 보고한다.

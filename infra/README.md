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

서버의 `.env`를 직접 편집해 긴 임의 secret을 넣는다. `.env`는 shell script가 아니며 `KEY=literal` 형식이다. shell substitution, 중복·알 수 없는 key, 공백이 있는 값은 거부된다. `POSTGRES_DB=ai_erp`, 일치하는 PostgreSQL/DB credential, `DB_URL=jdbc:postgresql://postgres:5432/ai_erp`, URL-safe Redis password와 정확히 일치하는 `redis://:<password>@redis:6379/0`, `SITE_ADDRESS=192.168.219.100`을 사용한다. Google credential은 두 값이 모두 비어 있거나 모두 있어야 하며 `APP_OIDC_ENABLED=true`이면 둘 다 필요하다. `APP_IMAGE`는 운영 env에 넣지 않는다. 배포 script가 commit SHA에서 직접 결정한다.

아래 명령을 정확한 clean checkout에서 실행한다. tracked 변경과 nonignored untracked 파일이 모두 없어야 한다. 운영에서 `AI_ERP_TEST_MODE`와 경로 override를 설정하지 않는다.

```sh
release=$(git rev-parse HEAD)
bash scripts/preflight.sh "$release"
bash scripts/deploy.sh "$release"
bash scripts/smoke.sh public "$release"
```

배포는 host 경로 검증과 실제 lock 획득 뒤 project-owned volume/network 및 80/443 publisher를 검사한다. 같은 이름의 legacy 또는 label 없는 resource가 있으면 중단한다. 기존 서비스의 중지는 별도 승인과 ID 확인 절차로만 수행한다. 이 script는 비프로젝트 container를 중지하거나 volume/image를 제거하지 않는다.

배포 순서는 immutable image build → PostgreSQL/Redis/Caddy health → `pg_dump -Fc`와 `pg_restore -l` 검증 → source 재검증 → external Flyway 한 번 → inactive app health·image 검증·internal smoke → 전체 Caddy candidate 검증·reload → public smoke·20초 관찰·public smoke → manifest와 state 원자 기록 → 이전 app 중지이다. 최초 빈 DB에도 migration 직전 backup을 만든다. app의 Spring Flyway는 비활성화한다.

`shared/active-state.json`은 현재 release/color/image와 직전 release/color를 기록한다. `shared/caddy/active-upstream.caddy`와 manifest, local image 및 running container가 모두 일치해야 다음 배포가 가능하다. 최초 상태는 state가 없고 app이 실행되지 않는 503 initialization이다. 같은 active SHA의 재실행은 public smoke만 수행하고 완료된 비활성 SHA의 재배포는 거부한다.

Rollback은 보존된 immutable image를 현재 inactive slot에서 검증하고 같은 Caddy/public-smoke transaction으로 전환한다. migration을 역실행하지 않으며 원래 manifest를 수정하지 않는다. 연속해서 인자 없이 실행하면 직전 active release로 다시 이동한다.

```sh
bash scripts/rollback.sh
bash scripts/rollback.sh <preserved-40-character-sha>
bash scripts/backup.sh <40-character-sha>
```

Release별 `manifest.json`과 checksum, `events.log`, `backups/postgres-<timestamp>-<pid>.dump` 및 같은 이름의 `.json` metadata를 mode 600으로 보존한다. 디렉터리는 mode 700이다. Backup metadata에는 dump·Compose·Caddy·migration·active-state checksum과 Flyway history 존재 여부가 들어간다. `BACKUP_RETENTION_DAYS`는 0–9999 범위의 정수이며 기본값은 14일이다. 보존 기간이 지난 해당 backup 파일만 제거한다.

Caddy validation/reload, public smoke, manifest/state 기록이 실패하면 이전 upstream과 state의 정확한 내용을 복원하고 Caddy를 reload한다. 첫 배포 실패는 503와 state 부재로 복원되어 재시도할 수 있다. 복구 자체가 실패하면 exit 2를 반환하고 transaction 파일을 보존한다. 이 경우 자동 재배포를 멈추고 `*.previous`, 현재 upstream/state, running container를 확인해 운영자가 복구한다. 프로세스 강제 종료나 전원 장애 후 남은 transaction 파일도 같은 방식으로 확인한다. 비밀이 포함되는 Compose 전체 config나 container environment를 로그에 출력하지 않는다.

Production image는 OpenAPI → typed client·Swagger → frontend → Spring Boot jar 순서로 생성하고, `/`, API, `/assets/api-docs/index.html`을 같은 release에서 제공한다. Caddy만 80/443을 공개하고 내부 TLS를 사용한다. Public smoke는 지정 IP에 `curl --insecure`를 사용하므로 브라우저 신뢰가 필요하면 Caddy root CA를 별도 배포한다.

검증은 다음과 같다. 첫 명령은 예시 env에 image key가 없으므로 검사할 image reference를 별도로 제공한다.

```sh
APP_IMAGE=ai-erp:config-check docker compose --env-file infra/.env.prod.example -f infra/compose.prod.yml config --quiet
bash scripts/tests/deployment-contract.sh
docker build --label "org.opencontainers.image.revision=$(git rev-parse HEAD)" --tag "ai-erp:$(git rev-parse HEAD)" .
```

Contract harness는 실제 shell 진입점을 strict stateful host double로 실행한다. Linux에서 실제 `flock` 경쟁과 POSIX 권한·symlink를 검사한다. Windows Git Bash 검증은 해당 Linux 기능과 실제 Docker 실행을 대체하지 못하며 이를 명시적으로 보고한다.

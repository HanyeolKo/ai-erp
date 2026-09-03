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

운영은 Caddy를 외부 단일 진입점으로 두고, 하나의 PostgreSQL/Redis를 두 애플리케이션 슬롯이 공유하는 Blue/Green 방향을 따른다. 마이그레이션은 expand-contract로 배포하고, 새 슬롯의 readiness 확인 후 전환·관찰하며 문제가 있으면 이전 슬롯으로 되돌린다.

운영 Compose, 자동 전환, Caddy 설정, 비밀 관리와 실제 배포는 아직 구현하지 않는다.

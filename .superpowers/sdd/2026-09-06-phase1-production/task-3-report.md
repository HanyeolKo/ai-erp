# Task 3 report: production packaging

## Scope

Implemented the approved deployment package only: immutable multi-stage application image, production Compose, Caddy configuration, bounded deployment scripts, and a fake-runtime deployment contract test.

## TDD evidence

`scripts/tests/deployment-contract.sh` was written before the scripts. Its first attempted execution was blocked because the native Windows `bash` command routes to uninstalled WSL; final checks use the installed Git Bash at `D:/Git/bin/bash.exe`. The contract test was then expanded while implementing the scripts and is retained as the regression suite.

The green contract test exercises first deployment, blue-to-green promotion, inactive-slot health failure before the switch, public-smoke failure after the switch with rollback, invalid SHA rejection, lock contention, and static safeguards against volume deletion, arbitrary removal, secret echo, and non-project container stops.

## Changed paths

- `Dockerfile`, `.dockerignore`
- `infra/Caddyfile`, `infra/compose.prod.yml`, `infra/.env.prod.example`
- `scripts/{preflight,deploy,smoke,rollback,backup}.sh`
- `scripts/tests/deployment-contract.sh`
- `README.md` deployment section

## Validation

- `D:/Git/bin/bash.exe -lc 'for f in scripts/*.sh scripts/tests/*.sh; do bash -n "$f"; done'` passed.
- `D:/Git/bin/bash.exe -lc 'bash scripts/tests/deployment-contract.sh'` passed.
- `git diff --check` passed.

## Not run locally

Docker CLI/daemon is unavailable in this Windows workspace. Therefore `docker compose ... config --quiet` and the multi-stage image build must run on the GitHub-hosted CI or the approved Linux deployment host. No SSH, GitHub access, image pull, or deployment was performed by this task.

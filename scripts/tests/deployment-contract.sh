#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
bash "$PROJECT/scripts/tests/error-trap-contract.sh"
# TEST_PYTHON selects an installed Windows runtime; Linux CI uses python3.
exec "${TEST_PYTHON:-python3}" "$PROJECT/scripts/tests/deployment_contract.py" "$PROJECT"

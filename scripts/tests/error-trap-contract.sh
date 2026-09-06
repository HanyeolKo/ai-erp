#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
T="$(mktemp -d)"
trap 'rm -f -- "$T/abort-count"; rmdir -- "$T"' EXIT
# A failing command substitution inherits ERR. Only the entrypoint process may
# compensate; otherwise the subshell and parent restore/remove the same files.
if bash -c '
  source "$1"
  marker="$2"
  abort_transaction() { printf "abort\n" >>"$marker"; }
  install_error_trap
  value="$(false)"
' _ "$PROJECT/scripts/lib-deploy.sh" "$T/abort-count"; then
  printf 'FAIL: substitution failure must propagate\n' >&2
  exit 1
fi
[[ "$(wc -l <"$T/abort-count")" == 1 ]] || { printf 'FAIL: abort must execute exactly once\n' >&2; exit 1; }
printf 'PASS: 1 scenario / 2 assertions (nonrecursive ERR compensation)\n'

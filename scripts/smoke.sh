#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_NAME=smoke
source "$(dirname -- "$0")/lib-deploy.sh"
MODE="${1:-}" VALUE="${2:-}"
[[ $# == 2 ]] || fail 'smoke requires mode and color/releaseId'
case "$MODE" in internal) [[ "$VALUE" == blue || "$VALUE" == green ]] || fail 'invalid smoke color';; public) valid_release "$VALUE" || fail 'invalid smoke releaseId';; *) fail 'invalid smoke mode';; esac
validate_base
load_env
export APP_IMAGE="${APP_IMAGE:-ai-erp:smoke}"
paths=(/actuator/health/readiness / /api/v1/system/configuration /api/v1/system/info /assets/api-docs/index.html)
curl_args=(--silent --show-error --connect-timeout 3 --max-time 10 --include)
request() {
  local path="$1" expected_status="$2" site="${3:-$SITE_ADDRESS}" response
  if [[ "$MODE" == internal ]]; then
    response="$("${COMPOSE[@]}" exec -T "app-$VALUE" curl "${curl_args[@]}" "http://127.0.0.1:8080$path" 2>/dev/null && printf '%s' '__AI_ERP_END__')" || fail 'internal smoke request failed'
  else
    # Preserve browser Host/SNI while avoiding public DNS hairpin routing and proxy overrides.
    response="$(curl "${curl_args[@]}" --insecure --noproxy '*' --resolve "$site:443:192.168.219.100" "https://$site$path" 2>/dev/null && printf '%s' '__AI_ERP_END__')" || fail 'public smoke request failed'
  fi
  # A redirect can have no body; retain its final header boundary through command substitution.
  response="${response%__AI_ERP_END__}"
  response="${response//$'\r'/}"
  [[ "$response" == *$'\n\n'* ]] || fail 'smoke response has no header boundary'
  headers="${response%%$'\n\n'*}"; body="${response#*$'\n\n'}"
  [[ "$headers" =~ ^HTTP/(1\.[01]|2|3)[[:space:]]$expected_status([[:space:]]|$) ]] || fail 'smoke response status mismatch'
  if [[ "$MODE" == public ]]; then
    [[ "$(grep -ic "^x-ai-erp-release: $VALUE$" <<<"$headers")" == 1 ]] || fail 'public response release header mismatch'
  fi
}
for path in "${paths[@]}"; do
  request "$path" 200
  [[ -n "$body" ]] || fail 'smoke response is empty'
  if [[ "$path" == /actuator/health/readiness ]]; then
    python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"' <<<"$body" 2>/dev/null || fail 'readiness is not UP'
  fi
  if [[ "$path" == /api/v1/system/configuration ]]; then
    python3 -c 'import json,sys; value=json.load(sys.stdin); enabled=sys.argv[1]=="true"; assert value["login"]==("READY" if enabled else "CONFIGURATION_REQUIRED"); assert value["loginUrl"]==("/oauth2/authorization/google" if enabled else None)' "$APP_OIDC_ENABLED" <<<"$body" 2>/dev/null || fail 'login configuration mismatch'
  fi
done
request /api/v1/me 401
if [[ "$MODE" == public && "$APP_OIDC_ENABLED" == true ]]; then
  login_site="$SITE_ADDRESS"
  request /oauth2/authorization/google 302
  if [[ "$login_site" == 192.168.219.100 ]]; then
    [[ "$(grep -ic '^location: https://ai-erp.duckdns.org/oauth2/authorization/google$' <<<"$headers")" == 1 ]] || fail 'IP login must redirect to the primary domain'
    login_site=ai-erp.duckdns.org
    request /oauth2/authorization/google 302 "$login_site"
  fi
  # Inspect the local redirect without following it or printing cookies, state or client IDs.
  python3 -c 'import os,sys; from urllib.parse import urlsplit,parse_qs; locations=[line.split(":",1)[1].strip() for line in sys.stdin if line.lower().startswith("location:")]; assert len(locations)==1; url=urlsplit(locations[0]); assert url.scheme=="https" and url.netloc=="accounts.google.com" and url.path=="/o/oauth2/v2/auth"; query=parse_qs(url.query); assert query.get("redirect_uri")==["https://"+sys.argv[1]+"/login/oauth2/code/google"]; assert query.get("response_type")==["code"] and query.get("client_id")==[os.environ["GOOGLE_CLIENT_ID"]] and len(query.get("state",[]))==1' "$login_site" <<<"$headers" 2>/dev/null || fail 'Google authorization redirect mismatch'
fi
printf 'smoke: ok mode=%s\n' "$MODE"

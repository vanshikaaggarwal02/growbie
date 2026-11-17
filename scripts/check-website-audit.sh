#!/usr/bin/env bash
# Live checks for the website audit. This crawls a real site, calls the real PageSpeed API and the
# real Claude API (a few cents), so run it occasionally.
# Usage: scripts/check-website-audit.sh [site]   (BASE defaults to http://localhost:8080)
# The finished audit is kept in /tmp/launchmate-audit.json.
set -u
BASE="${BASE:-http://localhost:8080}"
SITE="${1:-https://example.com}"
STAMP="$(date +%s)"
PW="correct-horse-battery"
PASS=0; FAIL=0
BODY=/tmp/launchmate-audit-step.json

userjson() { printf '{"email":"%s","password":"%s","displayName":"%s"}' "$1" "$2" "$3"; }

expect() { # name expected-status method path token [json]
  local name="$1" want="$2" method="$3" path="$4" token="$5" data="${6:-}"
  local args=(-s -m 60 -o "$BODY" -w "%{http_code}" -X "$method" "$BASE$path" -H "Content-Type: application/json")
  if [ -n "$token" ]; then args+=(-H "Authorization: Bearer $token"); fi
  if [ -n "$data" ]; then args+=(-d "$data"); fi
  local got; got="$(curl "${args[@]}")"
  if [ "$got" = "$want" ]; then echo "PASS  $name"; PASS=$((PASS+1))
  else echo "FAIL  $name (expected $want, got $got)"; FAIL=$((FAIL+1)); head -c 300 "$BODY"; echo; fi
}
contains() { if grep -q "$2" "$BODY"; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 (response lacks: $2)"; FAIL=$((FAIL+1)); fi; }
lacks() { if grep -q "$2" "$BODY"; then echo "FAIL  $1 (response has: $2)"; FAIL=$((FAIL+1)); else echo "PASS  $1"; PASS=$((PASS+1)); fi; }
field() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/" "$BODY"; }
firstid() { sed -E 's/^\{"id":"([^"]+)".*/\1/' "$BODY"; }

expect "register user A" 201 POST /api/v1/auth/register "" "$(userjson "wa$STAMP@example.test" "$PW" "Audit A")"
TOKEN_A="$(field token)"
expect "create business profile with a website" 201 POST /api/v1/profiles "$TOKEN_A" "{\"profileType\":\"EXISTING_BUSINESS\",\"displayName\":\"Audit Test\",\"websiteUrl\":\"$SITE\",\"businessDetails\":{\"category\":\"Test\"}}"
PID="$(firstid)"
expect "create business profile without a website" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"EXISTING_BUSINESS","displayName":"No Site"}'
NOSITE="$(firstid)"

echo "--- addresses that must never be fetched"
expect "no token rejected" 401 POST "/api/v1/profiles/$PID/website-audits" "" '{}'
expect "profile with no website and none given" 400 POST "/api/v1/profiles/$NOSITE/website-audits" "$TOKEN_A" '{}'
for bad in "http://127.0.0.1/" "http://localhost:8080/actuator/health" "http://169.254.169.254/latest/meta-data/" \
           "http://2130706433/" "http://[::1]/" "http://10.0.0.1/" "ftp://example.com" "file:///etc/passwd" \
           "https://user:pw@example.com" "http://example.com:8080/" "http://intranet/" "http://printer.local/"; do
  expect "rejects $bad" 400 POST "/api/v1/profiles/$PID/website-audits" "$TOKEN_A" "{\"websiteUrl\":\"$bad\"}"
  contains "  with the right error code" '"code":"INVALID_WEBSITE_URL"'
done
# A real public hostname whose DNS record points at 127.0.0.1: only a resolution-time check catches it.
expect "rejects a public name that resolves to loopback (localtest.me)" 400 POST "/api/v1/profiles/$PID/website-audits" "$TOKEN_A" '{"websiteUrl":"http://localtest.me/"}'
contains "  because it points at a private network" 'private or internal'
expect "page limit above 10 rejected" 400 POST "/api/v1/profiles/$PID/website-audits" "$TOKEN_A" '{"maxPages":99}'

echo "--- ownership"
expect "register user B" 201 POST /api/v1/auth/register "" "$(userjson "wb$STAMP@example.test" "$PW" "Audit B")"
TOKEN_B="$(field token)"
expect "B cannot start an audit on A's profile" 404 POST "/api/v1/profiles/$PID/website-audits" "$TOKEN_B" '{}'
expect "B cannot list A's audits" 404 GET "/api/v1/profiles/$PID/website-audits" "$TOKEN_B"

echo "--- live audit of $SITE (crawl + PageSpeed + Claude)"
expect "start audit" 202 POST "/api/v1/profiles/$PID/website-audits" "$TOKEN_A" '{"maxPages":4}'
AID="$(field auditId)"
contains "starts as queued" '"status":"QUEUED"'
expect "a second audit while one runs is refused" 409 POST "/api/v1/profiles/$PID/website-audits" "$TOKEN_A" '{}'
expect "B cannot read A's audit" 404 GET "/api/v1/website-audits/$AID" "$TOKEN_B"

STATUS="QUEUED"
for i in $(seq 1 90); do
  curl -s -m 30 -H "Authorization: Bearer $TOKEN_A" "$BASE/api/v1/website-audits/$AID" -o "$BODY"
  STATUS="$(grep -o '"status":"[A-Z]*"' "$BODY" | head -1 | cut -d'"' -f4)"
  case "$STATUS" in COMPLETED|PARTIAL|FAILED) break;; esac
  sleep 2
done
cp "$BODY" /tmp/launchmate-audit.json
echo "final status after polling: $STATUS"
if [ "$STATUS" = "COMPLETED" ]; then echo "PASS  audit completed"; PASS=$((PASS+1)); else echo "FAIL  audit did not complete (status $STATUS)"; FAIL=$((FAIL+1)); fi
contains "crawled at least one page" '"pages":\[{"url":"'
contains "PageSpeed data is present and real" '"available":true'
contains "PageSpeed has a performance score" '"performance":[0-9]'
contains "readiness checks were measured" '"key":"TITLE_PRESENT"'
contains "readiness summary is stated" '"readinessSummary":"[0-9]* of [0-9]* checks passed'
contains "a written summary exists" '"summary":"'
contains "findings have verified evidence" '"evidence":"'
contains "findings state their source" '"sourceLabel":"\(OBSERVED_PUBLIC_DATA\|AI_DERIVED\)"'
contains "limitations are listed" '"limitations":\["'
expect "audit list shows it" 200 GET "/api/v1/profiles/$PID/website-audits" "$TOKEN_A"
contains "list shows the final status" "\"status\":\"$STATUS\""

echo; echo "Passed: $PASS  Failed: $FAIL"
[ "$FAIL" -eq 0 ]

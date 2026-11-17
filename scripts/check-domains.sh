#!/usr/bin/env bash
# Live checks for the domain endpoints. These call the real GoDaddy API (read-only, no charges),
# so keep runs occasional: the provider allows about 60 requests a minute.
# Usage: scripts/check-domains.sh   (BASE defaults to http://localhost:8080)
set -u
BASE="${BASE:-http://localhost:8080}"
STAMP="$(date +%s)"
PW="correct-horse-battery"
PASS=0; FAIL=0
BODY=/tmp/launchmate-domains.json

userjson() { printf '{"email":"%s","password":"%s","displayName":"%s"}' "$1" "$2" "$3"; }

expect() { # name expected-status method path token [json]
  local name="$1" want="$2" method="$3" path="$4" token="$5" data="${6:-}"
  local args=(-s -o "$BODY" -w "%{http_code}" -X "$method" "$BASE$path" -H "Content-Type: application/json")
  if [ -n "$token" ]; then args+=(-H "Authorization: Bearer $token"); fi
  if [ -n "$data" ]; then args+=(-d "$data"); fi
  local got; got="$(curl "${args[@]}")"
  if [ "$got" = "$want" ]; then echo "PASS  $name"; PASS=$((PASS+1))
  else echo "FAIL  $name (expected $want, got $got)"; FAIL=$((FAIL+1)); fi
}
contains() { if grep -q "$2" "$BODY"; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 (response lacks: $2)"; FAIL=$((FAIL+1)); fi; }
lacks() { if grep -q "$2" "$BODY"; then echo "FAIL  $1 (response has: $2)"; FAIL=$((FAIL+1)); else echo "PASS  $1"; PASS=$((PASS+1)); fi; }
field() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/" "$BODY"; }

expect "register user A" 201 POST /api/v1/auth/register "" "$(userjson "da$STAMP@example.test" "$PW" "Domain A")"
TOKEN_A="$(field token)"
expect "create new-business profile" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"NEW_BUSINESS","displayName":"Bare Botanics","businessDetails":{"category":"Skincare"}}'
PID="$(field id)"

expect "no token rejected" 401 POST "/api/v1/profiles/$PID/domain-analyses" "" '{"domains":["example.com"]}'
expect "empty domain list rejected" 400 POST "/api/v1/profiles/$PID/domain-analyses" "$TOKEN_A" '{"domains":[]}'
expect "too many domains rejected" 400 POST "/api/v1/profiles/$PID/domain-analyses" "$TOKEN_A" '{"domains":["a1.com","a2.com","a3.com","a4.com","a5.com","a6.com","a7.com","a8.com","a9.com","a10.com","a11.com"]}'
expect "bad TLD filter rejected" 400 POST "/api/v1/profiles/$PID/domain-suggestions" "$TOKEN_A" '{"keyword":"bare botanics","tlds":["COM"]}'

echo "--- analysis without availability check"
expect "analyse without check" 200 POST "/api/v1/profiles/$PID/domain-analyses" "$TOKEN_A" '{"domains":["barebotanics.com"],"checkAvailability":false}'
contains "reports NOT_CHECKED" '"availability":"NOT_CHECKED"'
contains "brand match is exact" '"brandMatch":"EXACT"'
lacks "no checked time when unchecked" '"checkedAt":"2'

echo "--- live GoDaddy availability check"
expect "analyse with live check" 200 POST "/api/v1/profiles/$PID/domain-analyses" "$TOKEN_A" "{\"domains\":[\"barebotanics.com\",\"https://www.barebotanics.in/\",\"bad_domain\",\"BareBotanics.com\",\"launchmate-qa-$STAMP.com\"]}"
contains "rejects the invalid input" '"input":"bad_domain"'
contains "normalised the URL input" '"domain":"barebotanics.in"'
contains "returned a provider verdict" '"availability":"\(AVAILABLE\|UNAVAILABLE\)"'
contains "returned an indicative price" '"priceDisplay":"[0-9.]* USD"'
contains "records when it was checked" '"checkedAt":"2'
contains "includes the confirm-at-checkout note" 'Confirm the final price at checkout'
contains "source is GODADDY" '"source":"GODADDY"'
lacks "every checked name got a definitive answer (none UNKNOWN)" '"availability":"UNKNOWN"'

echo "--- live GoDaddy suggestions"
expect "suggestions" 200 POST "/api/v1/profiles/$PID/domain-suggestions" "$TOKEN_A" '{"keyword":"bare botanics","tlds":["com","in"],"limit":3}'
contains "suggestions are labelled as provider suggestions" '"origin":"PROVIDER_SUGGESTED"'
contains "suggestions are labelled available (GoDaddy lists available names only)" '"availability":"AVAILABLE"'
contains "suggestions carry the recheck warning" 'Availability can change, so confirm before buying'

echo "--- saved list and ownership"
expect "list saved domains" 200 GET "/api/v1/profiles/$PID/domains" "$TOKEN_A"
contains "saved list has the user candidate" '"origin":"USER_CANDIDATE"'
expect "register user B" 201 POST /api/v1/auth/register "" "$(userjson "db$STAMP@example.test" "$PW" "Domain B")"
TOKEN_B="$(field token)"
expect "B cannot analyse A's profile" 404 POST "/api/v1/profiles/$PID/domain-analyses" "$TOKEN_B" '{"domains":["example.com"]}'
expect "B cannot list A's domains" 404 GET "/api/v1/profiles/$PID/domains" "$TOKEN_B"
expect "B cannot request suggestions for A" 404 POST "/api/v1/profiles/$PID/domain-suggestions" "$TOKEN_B" '{"keyword":"x"}'

echo; echo "Passed: $PASS  Failed: $FAIL"
[ "$FAIL" -eq 0 ]

#!/usr/bin/env bash
# Live checks for brand suggestions and domain fit. These call the real Claude API (a few cents)
# and the real GoDaddy API (read-only), so run them occasionally, not in a loop.
# Usage: scripts/check-ai-domain.sh   (BASE defaults to http://localhost:8080)
# The raw responses are kept in /tmp/launchmate-brand.json and /tmp/launchmate-fit.json.
set -u
BASE="${BASE:-http://localhost:8080}"
STAMP="$(date +%s)"
PW="correct-horse-battery"
PASS=0; FAIL=0
BODY=/tmp/launchmate-ai.json

userjson() { printf '{"email":"%s","password":"%s","displayName":"%s"}' "$1" "$2" "$3"; }

expect() { # name expected-status method path token [json]
  local name="$1" want="$2" method="$3" path="$4" token="$5" data="${6:-}"
  local args=(-s -m 150 -o "$BODY" -w "%{http_code}" -X "$method" "$BASE$path" -H "Content-Type: application/json")
  if [ -n "$token" ]; then args+=(-H "Authorization: Bearer $token"); fi
  if [ -n "$data" ]; then args+=(-d "$data"); fi
  local got; got="$(curl "${args[@]}")"
  if [ "$got" = "$want" ]; then echo "PASS  $name"; PASS=$((PASS+1))
  else echo "FAIL  $name (expected $want, got $got)"; FAIL=$((FAIL+1)); head -c 400 "$BODY"; echo; fi
}
contains() { if grep -q "$2" "$BODY"; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 (response lacks: $2)"; FAIL=$((FAIL+1)); fi; }
lacks() { if grep -q "$2" "$BODY"; then echo "FAIL  $1 (response has: $2)"; FAIL=$((FAIL+1)); else echo "PASS  $1"; PASS=$((PASS+1)); fi; }
field() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/" "$BODY"; }
# A profile response starts with its own id; later ids (such as channel ids) must not be picked up.
firstid() { sed -E 's/^\{"id":"([^"]+)".*/\1/' "$BODY"; }

expect "register user A" 201 POST /api/v1/auth/register "" "$(userjson "ai$STAMP@example.test" "$PW" "AI A")"
TOKEN_A="$(field token)"

expect "create new-business profile" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"NEW_BUSINESS","displayName":"Bare Botanics","targetAudience":"Adults with sensitive skin","marketLocation":"Bengaluru, India","businessDetails":{"category":"Skincare","description":"Plant-based skincare for sensitive skin, launching soon.","productsServices":["Face cleanser","Moisturiser"]}}'
NEW_ID="$(field id)"
expect "create creator profile" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"YOUTUBE_CREATOR","displayName":"Aarav Creates","youtubeChannelUrl":"https://www.youtube.com/@aarav"}'
CREATOR_ID="$(firstid)"
expect "create existing-business profile with a current domain" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"EXISTING_BUSINESS","displayName":"Bare Botanics","websiteUrl":"https://example.com","targetAudience":"Adults with sensitive skin","marketLocation":"Bengaluru, India","businessDetails":{"category":"Skincare","description":"Plant-based skincare for sensitive skin.","currentDomain":"barebotanics.com"}}'
EXISTING_ID="$(field id)"

echo "--- validation and access"
expect "no token rejected" 401 POST "/api/v1/profiles/$NEW_ID/brand-suggestions" "" '{}'
expect "creator profile cannot use brand suggestions" 400 POST "/api/v1/profiles/$CREATOR_ID/brand-suggestions" "$TOKEN_A" '{}'
expect "name count above limit rejected" 400 POST "/api/v1/profiles/$NEW_ID/brand-suggestions" "$TOKEN_A" '{"count":20}'
expect "domain fit with nothing to assess rejected" 400 POST "/api/v1/profiles/$NEW_ID/domain-fit" "$TOKEN_A" '{"domains":[]}'

echo "--- live: brand suggestions (Claude + GoDaddy)"
expect "brand suggestions" 200 POST "/api/v1/profiles/$NEW_ID/brand-suggestions" "$TOKEN_A" '{"idea":"Gentle, minimal skincare that sensitive skin can trust","tone":["calm","trustworthy"],"count":4}'
cp "$BODY" /tmp/launchmate-brand.json
contains "returns names with taglines" '"tagline":"'
contains "returns positioning statements" '"positioning":\["'
contains "domains are labelled as AI suggested" '"origin":"AI_SUGGESTED"'
contains "domains carry live availability" '"availability":"\(AVAILABLE\|UNAVAILABLE\|UNKNOWN\)"'
contains "trademark limitation is stated" 'rademark'
contains "result was saved" '"contentId":"'
lacks "no domain claimed without a provider check" '"availability":"NOT_CHECKED"'

echo "--- live: domain fit (Claude + GoDaddy)"
expect "domain fit for existing business" 200 POST "/api/v1/profiles/$EXISTING_ID/domain-fit" "$TOKEN_A" '{"domains":["barebotanics.in","xyz123.shop"]}'
cp "$BODY" /tmp/launchmate-fit.json
contains "current domain is flagged" '"currentDomain":true'
contains "other domains are not flagged as current" '"currentDomain":false'
contains "every item has a verdict" '"verdict":"\(STRONG\|ACCEPTABLE\|WEAK\)"'
contains "current-domain review is present" '"currentDomainReview":{"domain":"barebotanics.com"'
contains "review includes migration risks" '"risks":\["'
contains "review includes a migration checklist" '"checklist":\["'
contains "premium fee is surfaced" 'remium'
contains "trademark limitation is stated" 'rademark'
contains "no warnings (AI step succeeded)" '"warnings":\[\]'

echo "--- ownership"
expect "register user B" 201 POST /api/v1/auth/register "" "$(userjson "aib$STAMP@example.test" "$PW" "AI B")"
TOKEN_B="$(field token)"
expect "B cannot get brand suggestions for A" 404 POST "/api/v1/profiles/$NEW_ID/brand-suggestions" "$TOKEN_B" '{}'
expect "B cannot assess A's domains" 404 POST "/api/v1/profiles/$EXISTING_ID/domain-fit" "$TOKEN_B" '{"domains":["example.com"]}'

echo; echo "Passed: $PASS  Failed: $FAIL"
[ "$FAIL" -eq 0 ]

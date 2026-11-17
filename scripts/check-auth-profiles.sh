#!/usr/bin/env bash
# End-to-end checks for auth, profiles and ownership against a running backend.
# Usage: scripts/check-auth-profiles.sh   (BASE defaults to http://localhost:8080)
# Works with the old bash 3.2 that ships with macOS: JSON bodies are never built inside $(...).
set -u
BASE="${BASE:-http://localhost:8080}"
STAMP="$(date +%s)"
PW="correct-horse-battery"
PASS=0; FAIL=0
BODY=/tmp/launchmate-check.json

userjson() { printf '{"email":"%s","password":"%s","displayName":"%s"}' "$1" "$2" "$3"; }
logininfo() { printf '{"email":"%s","password":"%s"}' "$1" "$2"; }

# expect <name> <expected-status> <method> <path> <token> [json-body]
expect() {
  local name="$1" want="$2" method="$3" path="$4" token="$5" data="${6:-}"
  local args=(-s -o "$BODY" -w "%{http_code}" -X "$method" "$BASE$path" -H "Content-Type: application/json")
  if [ -n "$token" ]; then args+=(-H "Authorization: Bearer $token"); fi
  if [ -n "$data" ]; then args+=(-d "$data"); fi
  local got
  got="$(curl "${args[@]}")"
  if [ "$got" = "$want" ]; then echo "PASS  $name"; PASS=$((PASS+1))
  else echo "FAIL  $name (expected $want, got $got)"; FAIL=$((FAIL+1)); fi
}
contains() { # name needle
  if grep -q "$2" "$BODY"; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 (response lacks: $2)"; FAIL=$((FAIL+1)); fi
}
field() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/" "$BODY"; }

expect "health is public" 200 GET /actuator/health ""

expect "register user A" 201 POST /api/v1/auth/register "" "$(userjson "a$STAMP@example.test" "$PW" "User A")"
TOKEN_A="$(field token)"
expect "duplicate email rejected (case-insensitive)" 409 POST /api/v1/auth/register "" "$(userjson "A$STAMP@example.test" "$PW" "Dup")"
expect "short password rejected" 400 POST /api/v1/auth/register "" "$(userjson "s$STAMP@example.test" "short" "S")"
expect "wrong password rejected" 401 POST /api/v1/auth/login "" "$(logininfo "a$STAMP@example.test" "wrong-password-1")"
expect "unknown email rejected" 401 POST /api/v1/auth/login "" "$(logininfo "nobody$STAMP@example.test" "$PW")"
expect "login works" 200 POST /api/v1/auth/login "" "$(logininfo "a$STAMP@example.test" "$PW")"

expect "no token is rejected" 401 GET /api/v1/profiles ""
expect "bad token is rejected" 401 GET /api/v1/profiles "not-a-real-token"

expect "business with bad website URL rejected" 400 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"EXISTING_BUSINESS","displayName":"Bare Botanics","websiteUrl":"ftp://example.com"}'
expect "unknown profile type rejected" 400 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"CREATOR","displayName":"X"}'
expect "creator without channel rejected" 400 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"YOUTUBE_CREATOR","displayName":"Aarav Creates"}'
expect "creator with non-YouTube link rejected" 400 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"YOUTUBE_CREATOR","displayName":"Aarav","youtubeChannelUrl":"https://example.com/@aarav"}'

expect "create existing business" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"EXISTING_BUSINESS","displayName":"Bare Botanics","websiteUrl":"https://example.com","targetAudience":"Adults with sensitive skin","marketLocation":"Bengaluru, India","businessDetails":{"category":"Skincare","productsServices":["Face cleanser","Moisturiser"]}}'
BIZ_ID="$(field id)"
contains "business details returned" "Face cleanser"

expect "create new business (pre-launch)" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"NEW_BUSINESS","displayName":"Idea Co","businessDetails":{"category":"Coffee","currentDomain":""}}'

expect "create youtube creator" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"YOUTUBE_CREATOR","displayName":"Aarav Creates","youtubeChannelUrl":"https://www.youtube.com/@aarav","creatorDetails":{"niche":"Budget travel","contentGoals":["Grow Shorts"]}}'
contains "creator channel returned" "youtube.com/@aarav"

expect "list own profiles" 200 GET /api/v1/profiles "$TOKEN_A"
contains "list includes business" "Bare Botanics"
expect "get own profile" 200 GET "/api/v1/profiles/$BIZ_ID" "$TOKEN_A"
expect "update own profile" 200 PUT "/api/v1/profiles/$BIZ_ID" "$TOKEN_A" '{"primaryGoal":"Clearer homepage"}'
contains "update saved" "Clearer homepage"

expect "register user B" 201 POST /api/v1/auth/register "" "$(userjson "b$STAMP@example.test" "$PW" "User B")"
TOKEN_B="$(field token)"
expect "B cannot read A's profile" 404 GET "/api/v1/profiles/$BIZ_ID" "$TOKEN_B"
expect "B cannot update A's profile" 404 PUT "/api/v1/profiles/$BIZ_ID" "$TOKEN_B" '{"primaryGoal":"hijacked"}'
expect "B's list is empty" 200 GET /api/v1/profiles "$TOKEN_B"
contains "B sees no profiles" '^\[\]$'

echo; echo "Passed: $PASS  Failed: $FAIL"
[ "$FAIL" -eq 0 ]

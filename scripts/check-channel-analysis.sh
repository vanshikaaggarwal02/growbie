#!/usr/bin/env bash
# Live checks for the YouTube channel analysis. This calls the real YouTube Data API (about 3 quota
# units out of 10,000 a day) and the real Claude API (a few cents), so run it occasionally.
# Usage: scripts/check-channel-analysis.sh [channel-link]   (BASE defaults to http://localhost:8080)
# SECOND_RUN=1 also runs a second analysis, to check that snapshots accumulate (another few cents).
# The finished analysis is kept in /tmp/launchmate-channel.json.
set -u
BASE="${BASE:-http://localhost:8080}"
CHANNEL="${1:-https://www.youtube.com/@GoogleDevelopers}"
STAMP="$(date +%s)"
PW="correct-horse-battery"
PASS=0; FAIL=0
BODY=/tmp/launchmate-channel-step.json

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
field() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/" "$BODY"; }
firstid() { sed -E 's/^\{"id":"([^"]+)".*/\1/' "$BODY"; }

# poll an analysis until it finishes; sets STATUS and copies the result to $2
wait_for() { # analysis-id out-file token
  STATUS="QUEUED"
  for i in $(seq 1 90); do
    curl -s -m 30 -H "Authorization: Bearer $3" "$BASE/api/v1/channel-analyses/$1" -o "$BODY"
    STATUS="$(grep -o '"status":"[A-Z]*"' "$BODY" | head -1 | cut -d'"' -f4)"
    case "$STATUS" in COMPLETED|PARTIAL|FAILED) break;; esac
    sleep 2
  done
  cp "$BODY" "$2"
}

expect "register user A" 201 POST /api/v1/auth/register "" "$(userjson "ch$STAMP@example.test" "$PW" "Channel A")"
TOKEN_A="$(field token)"
expect "create creator profile" 201 POST /api/v1/profiles "$TOKEN_A" "{\"profileType\":\"YOUTUBE_CREATOR\",\"displayName\":\"Dev Channel\",\"youtubeChannelUrl\":\"$CHANNEL\",\"targetAudience\":\"Software developers\",\"marketLocation\":\"Global\",\"primaryGoal\":\"Reach more developers with short product demos\",\"creatorDetails\":{\"niche\":\"Developer tools and APIs\",\"contentLanguage\":\"English\",\"contentGoals\":[\"Grow Shorts\",\"Explain new releases clearly\"]}}"
PID="$(firstid)"
expect "create business profile" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"EXISTING_BUSINESS","displayName":"Shop"}'
BIZ="$(firstid)"
expect "create creator with a legacy /c/ link" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"YOUTUBE_CREATOR","displayName":"Legacy","youtubeChannelUrl":"https://www.youtube.com/c/SomeCreator"}'
LEGACY="$(firstid)"
expect "create creator whose channel does not exist" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"YOUTUBE_CREATOR","displayName":"Ghost","youtubeChannelUrl":"https://www.youtube.com/@lm-no-such-9f3k2x"}'
GHOST="$(firstid)"

echo "--- validation and access"
expect "no token rejected" 401 POST "/api/v1/profiles/$PID/channel-analyses" "" '{}'
expect "business profile cannot run a channel analysis" 400 POST "/api/v1/profiles/$BIZ/channel-analyses" "$TOKEN_A" '{}'
expect "too few videos requested" 400 POST "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_A" '{"maxVideos":3}'
expect "too many videos requested" 400 POST "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_A" '{"maxVideos":100}'
expect "legacy /c/ link is refused with instructions" 400 POST "/api/v1/profiles/$LEGACY/channel-analyses" "$TOKEN_A" '{}'
contains "  with the right error code" '"code":"INVALID_CHANNEL_URL"'
contains "  and tells the user what to use" 'youtube.com/@'

echo "--- a channel that does not exist"
expect "analysis starts" 202 POST "/api/v1/profiles/$GHOST/channel-analyses" "$TOKEN_A" '{}'
GID="$(field analysisId)"
wait_for "$GID" /tmp/launchmate-channel-ghost.json "$TOKEN_A"
if [ "$STATUS" = "FAILED" ]; then echo "PASS  ends as FAILED"; PASS=$((PASS+1)); else echo "FAIL  expected FAILED, got $STATUS"; FAIL=$((FAIL+1)); fi
contains "  with a clear reason" 'No YouTube channel was found'

echo "--- ownership"
expect "register user B" 201 POST /api/v1/auth/register "" "$(userjson "chb$STAMP@example.test" "$PW" "Channel B")"
TOKEN_B="$(field token)"
expect "B cannot start an analysis on A's profile" 404 POST "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_B" '{}'
expect "B cannot list A's snapshots" 404 GET "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_B"

echo "--- live analysis of $CHANNEL (YouTube + Claude)"
expect "start analysis" 202 POST "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_A" '{"maxVideos":15}'
AID="$(field analysisId)"
contains "starts as queued" '"status":"QUEUED"'
expect "a second analysis while one runs is refused" 409 POST "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_A" '{}'
expect "B cannot read A's analysis" 404 GET "/api/v1/channel-analyses/$AID" "$TOKEN_B"
wait_for "$AID" /tmp/launchmate-channel.json "$TOKEN_A"
echo "final status after polling: $STATUS"
if [ "$STATUS" = "COMPLETED" ]; then echo "PASS  analysis completed"; PASS=$((PASS+1)); else echo "FAIL  analysis did not complete (status $STATUS)"; FAIL=$((FAIL+1)); fi
# Postgres stores JSON with its keys re-ordered, so these checks do not depend on key order.
contains "channel details are present" '"channelId":"UC'
contains "subscriber count is a real number or marked hidden" '"subscribers":[0-9]'
contains "cadence was measured" '"uploadsPerWeek":[0-9]'
contains "three format buckets exist" '"format":"LONG_FORM"'
contains "title style was measured" '"withNumber":[0-9]'
contains "videos were saved" '"videos":\[{"videoId":"'
contains "themes name real video titles" '"exampleTitles":\["'
contains "new video ideas were written" '"hook":"'
contains "ideas state a format" '"format":"\(SHORT\|LONG_FORM\)"'
if [ "$(grep -o 'Watch time, audience retention' "$BODY" | wc -l | tr -d ' ')" = "1" ]; then echo "PASS  standard limitations are listed once, not repeated"; PASS=$((PASS+1)); else echo "FAIL  the data limitation is repeated"; FAIL=$((FAIL+1)); fi
contains "a calendar was written" '"calendar":\[{"day":'
contains "limitations say private analytics are unavailable" 'Watch time, audience retention'
expect "snapshot list shows it" 200 GET "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_A"
contains "  with the measured totals" '"totalViews":[0-9]'
contains "  and the upload rate" '"uploadsPerWeek":[0-9]'

if [ "${SECOND_RUN:-0}" = "1" ]; then
  echo "--- second analysis (snapshots accumulate)"
  expect "start second analysis" 202 POST "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_A" '{"maxVideos":5}'
  wait_for "$(field analysisId)" /tmp/launchmate-channel-2.json "$TOKEN_A"
  if [ "$STATUS" = "COMPLETED" ]; then echo "PASS  second analysis completed"; PASS=$((PASS+1)); else echo "FAIL  second analysis status $STATUS"; FAIL=$((FAIL+1)); fi
  expect "snapshot list now has two entries" 200 GET "/api/v1/profiles/$PID/channel-analyses" "$TOKEN_A"
  if [ "$(grep -o '"analysisId"' "$BODY" | wc -l | tr -d ' ')" -ge 2 ]; then echo "PASS  two snapshots saved"; PASS=$((PASS+1)); else echo "FAIL  expected two snapshots"; FAIL=$((FAIL+1)); fi
fi

echo; echo "Passed: $PASS  Failed: $FAIL"
[ "$FAIL" -eq 0 ]

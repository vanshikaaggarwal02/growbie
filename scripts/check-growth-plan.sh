#!/usr/bin/env bash
# Live checks for the growth plan. This runs real analyses and real plans: a website audit, a brand
# suggestion call, a channel analysis and four plan generations, about 35 cents of Claude credit in
# all, plus a few YouTube quota units. Run it occasionally, not in a loop.
# Usage: scripts/check-growth-plan.sh        (BASE defaults to http://localhost:8080)
#        SKIP_CREATOR=1 scripts/check-growth-plan.sh   skips the YouTube part
# Finished plans are kept in /tmp/launchmate-plan-*.json
set -u
BASE="${BASE:-http://localhost:8080}"
STAMP="$(date +%s)"
PW="correct-horse-battery"
PASS=0; FAIL=0
BODY=/tmp/launchmate-plan-step.json

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
ok() { echo "PASS  $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL  $1"; FAIL=$((FAIL+1)); }
field() { sed -E "s/.*\"$1\":\"([^\"]+)\".*/\1/" "$BODY"; }
firstid() { sed -E 's/^\{"id":"([^"]+)".*/\1/' "$BODY"; }

# wait_for <path-prefix> <id> <out-file> <token>: polls until the job reaches a final state
wait_for() {
  STATUS="QUEUED"
  for i in $(seq 1 100); do
    curl -s -m 30 -H "Authorization: Bearer $4" "$BASE$1/$2" -o "$BODY"
    STATUS="$(grep -o '"status":"[A-Z]*"' "$BODY" | head -1 | cut -d'"' -f4)"
    case "$STATUS" in COMPLETED|PARTIAL|FAILED) break;; esac
    sleep 2
  done
  cp "$BODY" "$3"
}

# py_check <name> <file> <python-expression-using-p>: passes when the expression is true
py_check() {
  if python3 -c "import json,sys; p=json.load(open('$2')); sys.exit(0 if ($3) else 1)" 2>/dev/null; then ok "$1"; else bad "$1"; fi
}

MATRIX="{('HIGH','LOW'):'HIGH',('HIGH','MEDIUM'):'HIGH',('HIGH','HIGH'):'MEDIUM',('MEDIUM','LOW'):'MEDIUM',('MEDIUM','MEDIUM'):'MEDIUM',('MEDIUM','HIGH'):'LOW',('LOW','LOW'):'LOW',('LOW','MEDIUM'):'LOW',('LOW','HIGH'):'LOW'}"
RANK="lambda r: ({'HIGH':0,'MEDIUM':1,'LOW':2}[r['priority']], {'HIGH':0,'MEDIUM':1,'LOW':2}[r['impact']], {'LOW':0,'MEDIUM':1,'HIGH':2}[r['effort']])"

expect "register user A" 201 POST /api/v1/auth/register "" "$(userjson "gp$STAMP@example.test" "$PW" "Plan A")"
TOKEN_A="$(field token)"
expect "register user B" 201 POST /api/v1/auth/register "" "$(userjson "gpb$STAMP@example.test" "$PW" "Plan B")"
TOKEN_B="$(field token)"

echo "=== EXISTING BUSINESS"
expect "create existing business" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"EXISTING_BUSINESS","displayName":"Bare Botanics","websiteUrl":"https://example.com","targetAudience":"Adults with sensitive skin","marketLocation":"Bengaluru, India","primaryGoal":"Get first online orders","businessDetails":{"category":"Skincare","description":"Plant-based skincare for sensitive skin, made in small batches.","productsServices":["Face cleanser","Moisturiser"],"currentDomain":"example.com"}}'
BIZ="$(firstid)"
expect "no token rejected" 401 POST "/api/v1/profiles/$BIZ/growth-plans" ""
expect "plan needs an analysis first" 400 POST "/api/v1/profiles/$BIZ/growth-plans" "$TOKEN_A"
contains "  with the right error code" '"code":"ANALYSIS_NOT_READY"'
expect "B cannot start a plan on A's profile" 404 POST "/api/v1/profiles/$BIZ/growth-plans" "$TOKEN_B"
expect "B cannot list A's plans" 404 GET "/api/v1/profiles/$BIZ/growth-plans" "$TOKEN_B"

echo "--- run a website audit and a domain check first (real)"
expect "start audit" 202 POST "/api/v1/profiles/$BIZ/website-audits" "$TOKEN_A" '{"maxPages":3}'
AUD="$(field auditId)"
wait_for /api/v1/website-audits "$AUD" /tmp/launchmate-plan-audit.json "$TOKEN_A"
if [ "$STATUS" = "COMPLETED" ]; then ok "audit completed"; else bad "audit status $STATUS"; fi
expect "domain check" 200 POST "/api/v1/profiles/$BIZ/domain-analyses" "$TOKEN_A" '{"domains":["barebotanics.in"]}'

echo "--- generate the plan (real)"
expect "start plan" 202 POST "/api/v1/profiles/$BIZ/growth-plans" "$TOKEN_A"
P1="$(field planId)"
contains "starts as queued" '"status":"QUEUED"'
expect "a second plan while one runs is refused" 409 POST "/api/v1/profiles/$BIZ/growth-plans" "$TOKEN_A"
expect "B cannot read A's plan" 404 GET "/api/v1/growth-plans/$P1" "$TOKEN_B"
wait_for /api/v1/growth-plans "$P1" /tmp/launchmate-plan-1.json "$TOKEN_A"
echo "plan status: $STATUS"
if [ "$STATUS" = "COMPLETED" ]; then ok "plan completed"; else bad "plan status $STATUS"; fi
py_check "3 to 10 recommendations" /tmp/launchmate-plan-1.json "3 <= len(p['recommendations']) <= 10"
py_check "positions run 1..n with no gaps" /tmp/launchmate-plan-1.json "[r['position'] for r in p['recommendations']] == list(range(1, len(p['recommendations'])+1))"
py_check "priority always follows the impact and effort matrix" /tmp/launchmate-plan-1.json "all($MATRIX[(r['impact'], r['effort'])] == r['priority'] for r in p['recommendations'])"
py_check "recommendations are ranked by priority, impact, then effort" /tmp/launchmate-plan-1.json "[($RANK)(r) for r in p['recommendations']] == sorted(($RANK)(r) for r in p['recommendations'])"
py_check "every recommendation cites source ids and shows evidence" /tmp/launchmate-plan-1.json "all(r['sourceIds'] and len(r['evidence']) >= 4 and r['howToMeasure'] and r['suggestedAction'] for r in p['recommendations'])"
py_check "all start open" /tmp/launchmate-plan-1.json "all(r['status'] == 'OPEN' for r in p['recommendations'])"
py_check "website audit evidence was used" /tmp/launchmate-plan-1.json "p['inputs']['websiteAudit'] is not None and any(r['module'] == 'WEBSITE' for r in p['recommendations'])"
py_check "domains were considered" /tmp/launchmate-plan-1.json "p['inputs']['domainsConsidered'] >= 1"
py_check "progress counts add up" /tmp/launchmate-plan-1.json "p['progress']['total'] == p['progress']['open'] == len(p['recommendations'])"
py_check "no outcome promises in the plan text" /tmp/launchmate-plan-1.json "not any(w in json.dumps(p).lower() for w in ['guarantee', 'will increase', 'double your', 'go viral'])"

echo "--- track recommendations"
RID1="$(python3 -c "import json;print(json.load(open('/tmp/launchmate-plan-1.json'))['recommendations'][0]['id'])")"
RID2="$(python3 -c "import json;print(json.load(open('/tmp/launchmate-plan-1.json'))['recommendations'][1]['id'])")"
TITLE1="$(python3 -c "import json;print(json.load(open('/tmp/launchmate-plan-1.json'))['recommendations'][0]['title'])")"
expect "mark in progress with a note" 200 PATCH "/api/v1/recommendations/$RID1" "$TOKEN_A" '{"status":"IN_PROGRESS","note":"Started on the new heading"}'
contains "  status saved" '"status":"IN_PROGRESS"'
contains "  note saved" 'Started on the new heading'
expect "mark completed" 200 PATCH "/api/v1/recommendations/$RID1" "$TOKEN_A" '{"status":"COMPLETED"}'
contains "  completed time recorded" '"completedAt":"2'
contains "  earlier note kept" 'Started on the new heading'
expect "reopen clears the completed time" 200 PATCH "/api/v1/recommendations/$RID1" "$TOKEN_A" '{"status":"OPEN"}'
contains "  back to open" '"status":"OPEN"'
contains "  no completed time" '"completedAt":null'
expect "complete it again" 200 PATCH "/api/v1/recommendations/$RID1" "$TOKEN_A" '{"status":"COMPLETED","note":"Heading added"}'
expect "dismiss another" 200 PATCH "/api/v1/recommendations/$RID2" "$TOKEN_A" '{"status":"DISMISSED"}'
expect "invalid status rejected" 400 PATCH "/api/v1/recommendations/$RID1" "$TOKEN_A" '{"status":"DONE"}'
expect "overlong note rejected" 400 PATCH "/api/v1/recommendations/$RID1" "$TOKEN_A" "{\"status\":\"OPEN\",\"note\":\"$(printf 'x%.0s' $(seq 1 501))\"}"
expect "B cannot change A's recommendation" 404 PATCH "/api/v1/recommendations/$RID1" "$TOKEN_B" '{"status":"COMPLETED"}'
expect "unknown recommendation" 404 PATCH "/api/v1/recommendations/00000000-0000-0000-0000-000000000000" "$TOKEN_A" '{"status":"OPEN"}'
expect "plan shows the progress" 200 GET "/api/v1/growth-plans/$P1" "$TOKEN_A"
py_check "progress counts reflect the changes" "$BODY" "p['progress']['completed'] == 1 and p['progress']['dismissed'] == 1 and p['progress']['total'] == len(p['recommendations'])"

echo "--- regenerate: the new plan must know what is already done"
expect "start second plan" 202 POST "/api/v1/profiles/$BIZ/growth-plans" "$TOKEN_A"
P2="$(field planId)"
wait_for /api/v1/growth-plans "$P2" /tmp/launchmate-plan-2.json "$TOKEN_A"
if [ "$STATUS" = "COMPLETED" ]; then ok "second plan completed"; else bad "second plan status $STATUS"; fi
py_check "the plan saw the owner's progress" /tmp/launchmate-plan-2.json "p['inputs']['previousProgressItems'] == 2"
python3 - "$TITLE1" <<'EOF'
import json, sys
done = sys.argv[1].lower()
p = json.load(open('/tmp/launchmate-plan-2.json'))
repeats = [r['title'] for r in p['recommendations'] if r['title'].lower() == done]
print("INFO  second plan titles:", [r['title'][:50] for r in p['recommendations']])
print("INFO  a completed item recommended again verbatim:", repeats or "no")
EOF
expect "plan history lists both plans" 200 GET "/api/v1/profiles/$BIZ/growth-plans" "$TOKEN_A"
if [ "$(grep -o '"planId"' "$BODY" | wc -l | tr -d ' ')" -ge 2 ]; then ok "two plans saved"; else bad "expected two plans"; fi

echo "=== NEW BUSINESS (launch plan)"
expect "create new business" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"NEW_BUSINESS","displayName":"Verde Studio","targetAudience":"Small cafe owners in Pune","marketLocation":"Pune, India","primaryGoal":"Open for first customers within two months","businessDetails":{"category":"Coffee roasting","description":"Small-batch coffee beans roasted in Pune for local cafes.","productsServices":["House blend","Single-origin beans"]}}'
NEW="$(firstid)"
expect "a launch plan needs no analysis" 202 POST "/api/v1/profiles/$NEW/growth-plans" "$TOKEN_A"
P3="$(field planId)"
wait_for /api/v1/growth-plans "$P3" /tmp/launchmate-plan-3.json "$TOKEN_A"
if [ "$STATUS" = "COMPLETED" ]; then ok "launch plan completed"; else bad "launch plan status $STATUS"; fi
py_check "launch steps are included" /tmp/launchmate-plan-3.json "any(r['module'] == 'LAUNCH' for r in p['recommendations'])"
py_check "launch steps cite launch facts" /tmp/launchmate-plan-3.json "any(any(s.startswith('LAUNCH') for s in r['sourceIds']) for r in p['recommendations'] if r['module'] == 'LAUNCH')"

if [ "${SKIP_CREATOR:-0}" != "1" ]; then
  echo "=== YOUTUBE CREATOR"
  expect "create creator" 201 POST /api/v1/profiles "$TOKEN_A" '{"profileType":"YOUTUBE_CREATOR","displayName":"Dev Channel","youtubeChannelUrl":"https://www.youtube.com/@GoogleDevelopers","targetAudience":"Software developers","primaryGoal":"Grow Shorts with product demos","creatorDetails":{"niche":"Developer tools","contentLanguage":"English"}}'
  CR="$(firstid)"
  expect "a creator plan needs a channel analysis first" 400 POST "/api/v1/profiles/$CR/growth-plans" "$TOKEN_A"
  expect "start channel analysis" 202 POST "/api/v1/profiles/$CR/channel-analyses" "$TOKEN_A" '{"maxVideos":10}'
  wait_for /api/v1/channel-analyses "$(field analysisId)" /tmp/launchmate-plan-channel.json "$TOKEN_A"
  if [ "$STATUS" = "COMPLETED" ]; then ok "channel analysis completed"; else bad "channel analysis status $STATUS"; fi
  expect "start creator plan" 202 POST "/api/v1/profiles/$CR/growth-plans" "$TOKEN_A"
  wait_for /api/v1/growth-plans "$(field planId)" /tmp/launchmate-plan-4.json "$TOKEN_A"
  if [ "$STATUS" = "COMPLETED" ]; then ok "creator plan completed"; else bad "creator plan status $STATUS"; fi
  py_check "creator plan uses the channel analysis" /tmp/launchmate-plan-4.json "p['inputs']['channelAnalysis'] is not None and any(r['module'] == 'CHANNEL' for r in p['recommendations'])"
  py_check "creator plan priorities follow the matrix" /tmp/launchmate-plan-4.json "all($MATRIX[(r['impact'], r['effort'])] == r['priority'] for r in p['recommendations'])"
fi

echo; echo "Passed: $PASS  Failed: $FAIL"
[ "$FAIL" -eq 0 ]

# Gap analysis: approved prototype vs the application

Written 14 Nov 2026 after inspecting the prototype (`design/`), the frontend, the API, the schema and live runs.

## Root causes found

| Problem you saw | Root cause | Fix |
|---|---|---|
| YouTube analysis "failed" on a pasted channel link | The backend only accepted `https://…youtube.com/@handle`, `/channel/UC…` and `/user/…`. It rejected a link without `https://`, a bare `@name`, legacy `/c/name` and `youtube.com/name` links, with a generic message. | Links are normalised; `/c/` and vanity links are resolved through the same 1-unit API call; video or playlist links get a specific "paste the channel page" message. Checked with 11 URL forms. |
| Analysis finished as "Partial" (no written analysis) for some channels | Parser rejected the whole model output when one evidence line was 315 characters against a 300 limit; the retry hit the same limit. | Length limits raised (evidence is still verified word for word). Fireship and Veritasium, which failed before, now complete. |
| Shorts not separated from long-form | Format was guessed from length, three buckets, over a small mixed sample. Channels that post mostly long videos showed zero Shorts. | Uses YouTube's own Shorts and videos lists (observed, not guessed), channel-wide counts, at least 10 of each format in the sample, per-format metrics. Falls back to the length estimate (clearly labelled) if the lists are unavailable. |
| "1 page(s) were skipped because robots.txt…" with no detail | Crawler only counted skipped pages. | Crawler records each skipped page with URL and reason; audit stores a crawl report; the page shows what was read, what was left out, why, and whether coverage is complete. |
| No bottom navigation | It was never real navigation. In the prototype it was a "Mockup screens" switcher for reviewers. Product navigation per profile type was not built. | Real per-profile navigation with the same visual. |
| No audit history or comparison | List endpoint returned a thin row; no comparison; no UI. | History with severity counts, comparison endpoint and page. Older audits are never overwritten. |
| Comparison reported "resolved" findings on an unchanged site | The model words findings differently each run. | Each audit now stores the evidence text it was checked against; a finding is resolved only if its quoted evidence is absent from the newer evidence. Unchanged site: 0 resolved, 0 new. |

## Missing (in the prototype, not yet built)
- Business workspace dashboard: diagnosis headline, readiness squares, PageSpeed bars, "Do this first" panel, mini opportunity map. The overview is currently action cards.
- New-business launch checklist with "because" lines. The domain finder and name ideas work; the checklist exists only as launch items inside the growth plan.
- Content studio "Written only from these facts" panel and guardrail note; before/after for meta tags shows the original text only.
- Measure step as a status trail on the overview (history and comparison exist; the visual trail does not).
- Contact form, demo mode, sign-up rate limiting, deployment.

## Partially implemented
- Creator flow: analysis, formats, rhythm, ideas and calendar work. Studio YouTube types need a topic typed by hand; they do not yet prefill from the ideas list.
- Plan: recommendations, status and map work; "Draft the wording" opens the studio but does not pre-select the content type.
- Mobile: landing and plan were checked at 390px in the prototype; the real app screens have not been checked on mobile.
- Instagram: not started (out of scope for the first release).

## Broken
- Nothing known broken after the fixes above. Not yet tested: a deliberately failed job in each flow, and sign-out during a running job.

## Limits that stay (stated honestly in the product)
- YouTube public data has no watch time, retention, impressions or click-through; they are shown as unavailable.
- Audits read server HTML only and at most 10 pages.
- "Resolved" means the quoted evidence is gone from what was read, not proof of which change fixed it.

## Task: domain name fit assessment

You will receive an EVIDENCE object with a business profile and a list of domains. For every domain you get measured facts: the label, the extension, its length, the number of hyphens and digits, how closely the name matches the brand name (brandMatch), a list of flags, and, where it was checked, the availability, inventory type and indicative prices. Assess how well each domain suits the business and return one JSON object that follows the supplied schema.

### What each assessment must contain

- domain: copy the domain exactly as given.
- verdict: STRONG when the name matches or clearly contains the brand and has no meaningful drawbacks; ACCEPTABLE when it works but has a real drawback; WEAK when it has a serious drawback, such as a name that does not match the brand, hyphens or digits that make it hard to say aloud, or an unusual extension for a business that sells to the public.
- strengths and concerns: short, concrete points. Each must rest on a measured fact in the evidence: the brand match, the length, hyphens or digits, the extension, the flags, the availability or the price. Name the fact, for example "12 letters, no hyphens or digits". Do not add points that the evidence does not support.
- suggestion: one sentence on what the owner should do with this domain, for example keep it, check it again, or consider a shorter spelling. Do not tell the owner to buy anything.

### Rules for reasoning

- Availability for a domain that is not the current domain: when it is AVAILABLE, say it was available when checked and could change. When it is UNKNOWN or NOT_CHECKED, say it is not confirmed and do not guess. When it is UNAVAILABLE, say the name cannot be registered and weigh it as a serious concern.
- Availability for the current domain means the opposite. UNAVAILABLE is expected and healthy, because the name is already registered, presumably by the owner. Never list it as a concern and never let it lower the verdict. If the current domain shows AVAILABLE, that is unexpected: it may be unregistered or may have lapsed, so tell the owner to confirm in their registrar account that they own it and that it is renewed. If its availability is UNKNOWN or NOT_CHECKED, say its registration status was not confirmed.
- Premium names: for a domain that is not the current domain, when the inventory is PREMIUM or a premium fee is present, treat the fee as a significant concern and quote it exactly as given in the evidence. For the current domain, prices and premium fees describe what a new buyer would pay. They are not a cost to the owner, so do not count them as a concern or let them lower the verdict.
- Prices are indicative only. Quote them exactly as written in the evidence and never convert currencies.
- Do not make claims about search ranking, traffic, backlinks or the commercial value of a name. You have no data on those.
- You cannot check trademarks or whether another business uses a similar name. Always include that as a limitation.
- Never describe a score or ranking number. Use only the verdict words.

### The current domain

Exactly one domain may be marked isCurrentDomain: true. It is the address the business already uses. For that domain only, add one entry to currentDomainReviews. If no domain is marked as current, return an empty currentDomainReviews list.

- The default recommendation is KEEP_CURRENT. Choose CHANGE_NEEDS_CAREFUL_REVIEW only when the evidence shows a serious mismatch, for example a WEAK verdict because the domain does not match the brand the owner describes, and even then the wording must make clear that the decision belongs to the owner and that nothing should change until the risks are weighed.
- reasoning: two or three sentences explaining the recommendation from the evidence.
- risks: always cover these four, each in your own words and specific to this business: loss of search engine ranking and indexing during a move, inbound links from other sites that would break or lose value, disruption to email addresses that use the domain, and loss of recognition among existing customers.
- checklist: the practical steps of a careful migration, in order: keep the old domain registered, set up permanent redirects from every old page to its new page, move and test email, update the website's address in search tools, update business listings and social profiles, and monitor for broken pages afterwards.
- Never recommend registering, buying or switching to another domain as an immediate action.

### Limitations

Always include that trademark and existing-brand conflicts were not checked. Add any other limit that applies, such as availability not being checked.

### Limits

Provide exactly one assessment per domain given, no more and no fewer. Strengths: at most four items of 200 characters. Concerns: at most four items of 200 characters. Suggestion: 300 characters. Reasoning: 500 characters. Risks: four to six items of 300 characters. Checklist: four to ten items of 250 characters. Limitations: at most five items.


siteFacts: when the evidence contains siteFacts, they were measured on the business's own website (does the name resolve, HTTPS, redirects, the extension). Treat them as verified. A technical problem there is a reason to fix the website setup; it is not by itself a reason to change the domain name. Say clearly when a fact is UNAVAILABLE and never fill it in. Never state or guess a domain's age, expiry or registrar.

Keep the current domain unless the evidence shows a meaningful branding, business or technical reason. A different name being available is never a reason on its own. If you do recommend reviewing a change, the reasoning must say what is limiting about the current domain, why a change could help, the SEO, branding and migration risks, and whether the benefit justifies the effort and risk. If the current domain is suitable, say so plainly and recommend keeping it.

Write for a business owner, not an engineer. Never repeat internal field names, flag codes or labels from the evidence, such as CONTAINED_IN_BRAND, UNCOMMON_TLD, NOT_CHECKED, siteFacts, measuredFacts or EXTENSION. Say what they mean in plain words instead (for example "the name contains a word from your brand" or "availability was not checked").

# Split Manager — Level of Effort (LOE): Implementation, Testing & Launch

*Companion documents: `brd.md` (why/what), `hld.md` (architecture), `lld.md` (implementation detail this estimate is built from). Effort is broken down by the same packages the LLD defines: `infra/`, `api/`, `frontend/`, `integ-tests/`, plus setup and launch work that spans all of them. `lambdas/` is out of scope for this estimate — it's explicitly deferred past P0 in the BRD/HLD.*

## Assumptions

- **One developer**, working solo, with AI-assisted scaffolding (this is not a team estimate with parallelized workstreams).
- Estimates are **effort hours** — focused working time, not elapsed calendar time. A "part-time pace" and "focused pace" conversion is given at the end so this maps to however much time is actually available per day.
- The LLD is treated as settled — these numbers assume no further architecture changes mid-build. A material scope change (e.g., adding auth, adding history/reporting) would need a new estimate for that slice, not a revision of this one.
- AWS account access and a GitHub account are assumed available going in (they're tracked as prerequisites below, not blockers baked silently into other numbers). A Splitwise account is useful for manually pasting the finalized summary, but no longer a build dependency since Splitwise's public API is gone.
- Manual, human-only steps that can't be scripted (the CodeStarConnections GitHub OAuth handshake, in particular) are called out explicitly — they cost calendar time waiting on the user, not developer effort, but are included since they gate progress.
- First-time AWS wiring (ECS/ALB networking, IAM grants, CDK/TypeScript tooling versions) has caused real friction in earlier attempts at this project — the infra phase includes a debugging buffer for that reason, not because the CDK code itself is expected to be large.

## Effort by phase

### Phase 0 — Setup & prerequisites

| Task | Hours |
|---|---|
| Confirm AWS account access, configure local credentials | 1.0 |
| Create the GitHub monorepo | 0.5 |
| Local dev environment: Java 21 + Maven, Node + npm, AWS CDK CLI, Docker, DynamoDB Local | 2.0 |
| **Subtotal** | **3.5** |

### Phase 1 — `infra/` (CDK, TypeScript)

| Task | Hours |
|---|---|
| CDK app scaffold: `PipelineStack`, `AppStage` | 3.0 |
| `DataStack` — DynamoDB table (TTL) + S3 bucket (lifecycle rule) | 2.0 |
| `ApiStack` — ECS Fargate + ALB + task IAM role (DynamoDB/S3/Textract grants) | 4.0 |
| `FrontendStack` — S3 + CloudFront | 2.0 |
| `cdk bootstrap` + first deploy; CodeStarConnections GitHub OAuth handshake (manual, in AWS Console) | 1.5 |
| End-to-end pipeline verification on minimal/empty stacks; debug first-deploy issues | 3.0 |
| **Subtotal** | **15.5** |

### Phase 2 — `api/` (Java, Spring Boot, Maven)

| Task | Hours |
|---|---|
| Spring Boot + Maven skeleton, Dockerfile | 2.0 |
| Controllers (`Receipt`, `Session`, `Expense`) stubbed + DTOs | 1.5 |
| `TextractClient` + `ReceiptParsingService` (mapping `SummaryFields`/`LineItemGroups`) | 4.0 |
| `ReceiptSessionRepository` (DynamoDB Enhanced Client) + `ReceiptSessionService` | 3.0 |
| `SplitCalculationService` — equal + by-item algorithms, rounding-remainder handling | 5.0 |
| `SplitSummaryService` — per-person breakdown + shareable text formatting | 1.5 |
| CORS config | 0.5 |
| Deploy to Beta, manual smoke test, debug | 3.0 |
| **Subtotal** | **20.5** |

### Phase 3 — `frontend/` (React + TypeScript)

| Task | Hours |
|---|---|
| Vite + React + TS scaffold, routing, API client | 2.0 |
| `ReceiptReviewSection` (editable items) | 3.0 |
| `TipEntrySection` (presets + manual override) | 1.5 |
| `ParticipantsSection` (free-text participant name entry, add/remove) | 1.5 |
| `SplitModeToggle` + `ItemAssignmentGrid` (tap-to-assign UI) | 5.0 |
| `SplitSummary` (client-side preview mirroring the backend's split algorithm) | 3.0 |
| `ConfirmButton` + `ConfirmationModal` + finalize flow (shows shareable summary + copy action) | 2.5 |
| Mobile-responsive styling pass (used inside an in-Shortcut web view on a phone) | 3.0 |
| Deploy to Beta, manual smoke test, debug | 3.0 |
| **Subtotal** | **24.5** |

### Phase 4 — iOS Shortcut & end-to-end integration

| Task | Hours |
|---|---|
| Build the Shortcut: share-sheet trigger, HEIC→JPEG conversion, POST, open web view | 2.0 |
| End-to-end manual testing against Beta with real receipts, several iterations | 4.0 |
| Bug-fixing pass across API/frontend from issues found in e2e testing | 5.0 |
| **Subtotal** | **11.0** |

### Phase 5 — Automated testing

| Task | Hours |
|---|---|
| Unit tests: `SplitCalculationService` edge cases (single item, uneven sharers, rounding remainder, zero tip) | 3.0 |
| Unit tests: `SplitSummaryService` (breakdown correctness, text formatting) | 1.0 |
| `integ-tests/` package scaffold (JUnit + REST Assured) | 1.5 |
| `ParseReceiptIntegTest`, `SessionIntegTest`, `FinalizeSplitIntegTest` | 3.0 |
| Wire IntegTests into CDK Pipelines' Beta-stage `addPost()`; verify a failure actually blocks Prod | 2.0 |
| **Subtotal** | **10.5** |

### Phase 6 — Launch

| Task | Hours |
|---|---|
| First automatic promotion to Prod; verify the deployed stack | 1.0 |
| Point the iOS Shortcut at the Prod API URL; final real-world test | 1.0 |
| Verify DynamoDB TTL cleanup and the S3 lifecycle rule actually reclaim storage in practice | 1.0 |
| CloudWatch logs/alarms sanity check | 1.0 |
| Security follow-up: rotate the GitHub PAT shared earlier in the project, confirm no secrets in git history | 1.0 |
| Documentation wrap-up (live URLs, runbook notes) | 1.0 |
| **Subtotal** | **6.0** |

## Totals

| | Hours |
|---|---|
| Phase 0 — Setup & prerequisites | 3.5 |
| Phase 1 — `infra/` | 15.5 |
| Phase 2 — `api/` | 20.5 |
| Phase 3 — `frontend/` | 24.5 |
| Phase 4 — iOS Shortcut & e2e integration | 11.0 |
| Phase 5 — Automated testing | 10.5 |
| Phase 6 — Launch | 6.0 |
| **Base total** | **91.5** |
| Contingency (~15%, for first-time AWS wiring and third-party-API surprises) | ~14.0 |
| **Estimated total** | **~106 hours** |

## Calendar-time conversion

| Pace | Hours/day | Elapsed time |
|---|---|---|
| Focused (treated like a short full-time sprint) | ~6 | ~18 working days (~3.5 weeks) |
| Part-time (evenings/weekends around other commitments) | ~4 | ~27 sessions (spread over ~5-7 weeks depending on how many days/week are worked) |

## Sequencing

Phases 1-3 (`infra/`, `api/`, `frontend/`) can start in parallel once Phase 0 is done, since each has its own skeleton milestone independent of the others — but Phase 4 (real end-to-end testing) can't meaningfully start until all three have at least a working Beta deployment. Phase 5's `integ-tests/` depends on the `POST /finalize-split` endpoint existing in `api/`, so it naturally follows Phase 2. Phase 6 depends on everything before it being green.

## Risks that could move this estimate

- **CodeStarConnections' GitHub OAuth handshake is a manual, human-in-the-loop step** — it can't be scripted or automated, and past attempts on this project have had the pipeline sit blocked on it. Budget calendar slack around Phase 1, not just developer hours.
- **First-time AWS resource wiring has caused real issues before** on this exact project (a DynamoDB `ServicePrincipal` grant error, `ts-node`/TypeScript version incompatibilities) — Phase 1's debugging buffer exists because of that history, not speculation.
- **Textract's real-world accuracy on messy/handwritten receipts is unverified** until Phase 4's real-receipt testing — if accuracy is worse than expected, Phase 4's bug-fixing line item absorbs some of that, but a significant shortfall could mean revisiting `ReceiptParsingService`'s mapping logic beyond what's budgeted.
- **If a comparable third-party expense-tracking API becomes available later**, wiring direct submission back in (client, request builder, auth) would need its own LOE addendum — this estimate assumes manual copy/paste handoff for the whole first release.

## Explicitly not estimated (out of scope)

Per the BRD's non-goals and the HLD/LLD's deferred items: the `lambdas/` package, any authentication/authorization layer, percentage-split mode, automatic posting to Splitwise or any other third-party service, permanent history/reporting, and bank/statement reconciliation. Any of these becoming in-scope would need its own LOE addendum.

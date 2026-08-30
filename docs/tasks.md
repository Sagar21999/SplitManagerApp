# Split Manager — v2 Task List

*Derived from `brd.md`, `hld.md`, `lld.md`. Deliberately lightweight: phases and checkable tasks, no estimates. Status: `[ ]` not started · `[~]` in progress · `[x]` done*

**Ground rules for v2**

- **Beta is the working environment.** All verification happens there. Prod exists in the pipeline and promotes automatically, but a broken Prod is tolerable — a broken Beta is not.
- **No integration tests, no promotion gate.** Unit tests plus the manual Beta checklist (LLD §11) are the quality bar.
- **Auth lands before the ledger is deployed.** Deploying a permanent transaction store behind the current unauthenticated ALB — even to Beta — publishes financial data. This is the one hard sequencing constraint.

---

## Phase 0 — Clear the decks

- [x] Delete the `integ-tests/` package
- [x] Remove the `IntegTests` `CodeBuildStep` from `PipelineStack` (keep both Beta and Prod stages)
- [~] Verify the pipeline still deploys Beta green after the gate removal — `cdk synth` clean locally; pending an actual pipeline run

## Phase 1 — Auth *(blocking; nothing else deploys until this is done)*

- [x] `AuthStack`: Cognito user pool (self-signup off), hosted-UI domain
- [x] Wire `AuthStack` through `AppStage` into `ApiStack` (issuer env var) and `FrontendStack`
- [x] **Serve the API over HTTPS via a `/api/*` CloudFront behavior** — the ALB is plain HTTP, so an HTTPS page could not call it at all (mixed content). Also makes the SPA and API same-origin.
- [x] `api/`: `server.servlet.context-path=/api`; ALB health check follows to `/api/actuator/health`
- [x] `api/`: `spring-boot-starter-oauth2-resource-server`, `SecurityConfig`, fail-closed `JwtDecoder`
- [x] `api/`: `WebConfig` CORS no longer defaults to `*`; empty means same-origin only
- [x] `frontend/`: `AuthProvider`, `RequireAuth`, PKCE login, `/auth/callback`, bearer token in `apiClient`
- [x] Token storage: access token in memory, refresh token in `sessionStorage`, nothing in `localStorage`
- [x] **Deployed to Beta and verified: unauthenticated `/api/transactions`, `/api/balances`, `/api/people` all return 401; `/api/actuator/health` returns 200 UP** (FR25 — repeat every deploy)
- [ ] Provision the single user account in the Beta pool *(user action)*
- [ ] Verify hosted-UI login lands back in the app with a working session *(user action)*

**Beta environment**
- Frontend / API: `https://d3frwzr61jzv4k.cloudfront.net`
- Cognito pool: `us-east-1_tly0tdcrR` · client `4dtt6h8pnebl35iitm918tbph1`
- Hosted UI: `https://split-manager-beta-548171705631.auth.us-east-1.amazoncognito.com`

## Phase 2 — Persistent ledger (backend)

- [x] `DataStack`: TTL dropped, PITR on, GSI1 (chronological) + GSI2 (dedup), statements bucket, ledger table `RETAIN`
- [x] Models: `Transaction`, `TransactionType`, `TransactionStatus`, `LineItem`, `SplitDefinition`, `SplitMode` (5 modes), `FinalizedSplit`, `Person`, `Participants`
- [x] `TransactionRepository`, `PersonRepository` on the single-table key design (LLD 4.2)
- [x] `TransactionService` — create, list, get, update draft, finalize, status transitions, ownership checks
- [x] `PersonService` — directory CRUD + `resolveOrCreate` on finalize (FR13), archive not delete
- [x] `BalanceService` — computed on read over OPEN/EXTERNALLY_ADDED transactions
- [x] Refactor `SplitCalculationService` to the unified weight pipeline (LLD 5), all five modes
- [x] `TransactionController`, `PersonController`, `BalanceController`, `ReimbursementController`
- [x] `CurrentUser` — userId always from the JWT `sub`, never the request
- [x] Delete `ReceiptSession`, `SessionStatus`, its repository/service, `SessionController`, `ExpenseController`, `ReceiptController`, `SessionNotFoundException`, `ReceiptItem`
- [x] Unit tests: all five split modes, the sum-exactly-to-total invariant, status transitions, summary rendering (26 passing)
- [ ] Deploy to Beta and smoke-test the new endpoints with a real token

## Phase 3 — Ledger UI

- [ ] Multi-route shell + nav (Ledger | Capture | Import | Reimbursements | People)
- [ ] `LedgerPage`: transaction list, filters, `BalanceSummaryBar`
- [ ] `TransactionDetailPage`: breakdown, share text + copy, `StatusActionBar`
- [ ] `PeoplePage` + `PersonPicker` (saved directory, inline add)
- [ ] `SplitEditorPage`: carry over receipt review / tip / item assignment; add `PayerSelector`, extend `SplitModeToggle` to five modes, add `WeightEntryGrid`
- [ ] `ReceiptCapturePage` (absorbs the v1 upload page)
- [ ] TanStack Query for server state; delete `useReceiptSession` and the v1 single-route `SplitPage`
- [ ] Mobile-responsive pass
- [ ] Deploy to Beta, click through the full receipt → split → finalize → status flow

## Phase 4 — CSV statement import + dedup

- [ ] `StatementImport` / `StatementCandidate` models + repository
- [ ] `CsvStatementParser` + `issuer-profiles.yml`; discard credits
- [ ] `StatementClassificationService` — user history → reimbursement keywords → split heuristics
- [ ] `DeduplicationService` — GSI2 lookup, ±3-day window, normalized fuzzy merchant match
- [ ] `StatementIngestionService` — store, parse, classify, dedup-check, **delete the raw S3 object**
- [ ] `StatementController` + `StatementImportPage` / `CandidateReviewPage`
- [ ] Duplicate warnings on the receipt path too
- [ ] Unit tests: parser, classifier, dedup edge cases
- [ ] Verify on Beta with a real CSV export

## Phase 5 — PDF statement parsing *(highest risk — prototype first)*

- [ ] **Prototype against one real PDF statement before building anything around it**
- [ ] `TextractDocumentClient` (TABLES), `bedrock:InvokeModel` IAM grant
- [ ] `BedrockClient.normalizeStatementRows` — Converse, JSON-schema output, temperature 0
- [ ] `PdfStatementParser` behind the existing `StatementParser` interface
- [ ] Partial-parse handling: report dropped row counts rather than failing the import
- [ ] Unit tests against recorded Textract output with `BedrockClient` stubbed (never call Bedrock in CI)
- [ ] Verify on Beta with a real PDF statement

## Phase 6 — Reimbursements

- [ ] `ReimbursementsPage` — filtered ledger view
- [ ] `generateReimbursementSummary` + export action (FR23)
- [ ] Verify the Uber/transit path end to end on Beta

## Phase 7 — Wrap-up

- [ ] Full manual Beta checklist (LLD §11) in one pass
- [ ] Confirm the statements bucket actually empties; receipt images retained
- [ ] CloudWatch logs/alarms sanity check
- [ ] Rotate the GitHub PAT shared earlier in the project; confirm no secrets in git history
- [ ] README: live Beta URL, login notes, runbook

---

## Deferred (revisit only if needed)

- Reinstating `integ-tests/` and the Prod promotion gate — required before Prod becomes load-bearing
- `lambdas/` — only if PDF parsing latency exceeds the ALB idle timeout
- Receipt QR decoding, email receipt ingestion, parse-confidence surfacing, spending reports (BRD "Future scope")

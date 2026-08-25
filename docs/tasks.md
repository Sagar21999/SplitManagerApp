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
- [x] `api/`: `server.servlet.context-path=/api` to match the forwarded prefix; ALB health check follows to `/api/actuator/health`
- [x] `api/`: `spring-boot-starter-oauth2-resource-server`, `SecurityConfig`, fail-closed `JwtDecoder`; `/actuator/health` stays public
- [x] `api/`: `WebConfig` CORS no longer defaults to `*`; empty means same-origin only
- [x] `frontend/`: `AuthProvider`, `RequireAuth`, PKCE login, `/auth/callback` code exchange, bearer token in `apiClient`
- [x] Token storage: access token in memory, refresh token in `sessionStorage`, nothing in `localStorage`
- [ ] Provision the single user account manually in the Beta pool *(needs AWS console)*
- [ ] **Verify on Beta: an unauthenticated request returns 401** (FR25 — repeat this check every deploy)
- [ ] Verify on Beta: hosted-UI login lands back in the app with a working session

## Phase 2 — Persistent ledger (backend)

- [ ] `DataStack`: drop the TTL attribute, enable PITR, add GSI1 (chronological) and GSI2 (dedup), add the statements bucket
- [ ] Models: `Transaction`, `TransactionType`, `TransactionStatus`, `LineItem`, `SplitDefinition`, `SplitMode`, `FinalizedSplit`, `Person`
- [ ] `TransactionRepository`, `PersonRepository` on the single-table key design (LLD §4.2)
- [ ] `TransactionService` — create, list, get, update, finalize, status transitions
- [ ] `PersonService` — directory CRUD + `resolveOrCreate` on finalize (FR13)
- [ ] `BalanceService` — computed on read over open transactions
- [ ] Refactor `SplitCalculationService` to the unified weight pipeline (LLD §5), all five modes
- [ ] `TransactionController`, `PersonController`, `BalanceController`
- [ ] Delete `ReceiptSession`, `SessionStatus`, its repository/service, `SessionController`, `ExpenseController`, `SessionNotFoundException`
- [ ] Unit tests: all five split modes, the sum-exactly-to-total invariant, illegal status transitions
- [ ] Deploy to Beta and smoke-test the endpoints

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

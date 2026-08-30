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
- [x] Provision the single user account in the Beta pool — `sagar21naidu@gmail.com`, CONFIRMED, sub `e418e4e8-60a1-7059-818a-07935039fba8`
- [x] Verified hosted-UI login end to end: PKCE redirect (S256) -> sign in -> `/auth/callback` code exchange -> lands on `/` signed in; reload silently refreshes via `POST /oauth2/token` 200 with no re-login and no console errors

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
- [x] Deployed to Beta: `split-manager-beta-ledger` ACTIVE with pk/sk + GSI1/GSI2 and no TTL; all four endpoints 401 unauthenticated, health 200

## Phase 3 — Ledger UI

- [x] Multi-route shell + nav (Ledger | Add | Claims | People) on react-router v7
- [x] `LedgerPage`: transaction list, status filters, `BalanceSummaryBar`
- [x] `TransactionDetailPage`: items, split breakdown, `ShareTextPanel` + copy, `StatusActionBar`, delete
- [x] `PeoplePage` + `PersonPicker` (saved directory, inline add, rename, archive)
- [x] `SplitEditorPage`: receipt review / tip / totals; `PayerSelector`, 5-mode `SplitModeToggle`, `WeightEntryGrid`, `ItemAssignmentGrid`, live `SplitSummary`
- [x] `ReceiptCapturePage` (absorbs the v1 upload page)
- [x] TanStack Query for server state + invalidation; deleted `useReceiptSession`, `SplitPage`, `UploadPage`, `ParticipantsSection`, `ConfirmButton`, `ConfirmationModal`
- [x] `splitCalculation.ts` rewritten to mirror the API's 5-mode weight pipeline
- [x] Mobile-responsive pass; light/dark via `prefers-color-scheme`
- [x] `react-router-dom` v7 (v6 shipped an open-redirect advisory in the production bundle)
- [x] Deployed to Beta; `config.json` now serves `apiUrl: /api` plus Cognito config
- [ ] Click through receipt -> split -> finalize -> status on Beta *(user action)*

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

## Known issues

- **Pipeline does not trigger on push.** Two pushes produced zero executions; both runs needed `aws codepipeline start-pipeline-execution`. Cause: `DetectChanges` was absent from the synthesized template because CDK only emits it when `triggerOnPush` is passed explicitly. Fixed in `pipeline-stack.ts`, but the fix only takes effect after a run whose `UpdatePipeline` applies it — so expect one more manual start. If pushes still do not trigger after that, the GitHub App installation behind the connection needs reauthorizing in the console.
- **Orphaned v1 table.** `split-manager-beta-receipt-sessions` (and the Prod equivalent) are left behind by the rename to `-ledger` and can be deleted by hand.
- A newly-typed person cannot be chosen as the payer until after one finalize — they have no id until the API creates them. `PayerSelector` says so rather than hiding them.

## Deferred (revisit only if needed)

- Reinstating `integ-tests/` and the Prod promotion gate — required before Prod becomes load-bearing
- `lambdas/` — only if PDF parsing latency exceeds the ALB idle timeout
- Receipt QR decoding, email receipt ingestion, parse-confidence surfacing, spending reports (BRD "Future scope")

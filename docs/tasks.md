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
- [x] Verify the pipeline still deploys Beta green after the gate removal — a full run succeeded end to end on 2026-08-30

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

- [x] `StatementImport` / `StatementCandidate` models + `StatementImportRepository` (both in the `IMPORT#{id}` partition, so one Query returns the import and its rows)
- [x] `CsvStatementParser` + `issuer-profiles.yml` (Chase, Amex, Capital One, Citi, Discover); credits discarded; header inference when no profile is given
- [x] `StatementClassificationService` — user history → reimbursement keywords → split heuristics
- [x] `DeduplicationService` — GSI2 lookup, ±3-day window, Levenshtein merchant match at 0.85
- [x] `StatementIngestionService` — store, parse, classify, dedup-check, **delete the raw S3 object** (in a `finally`, so a failed parse still deletes)
- [x] `StatementController` + `StatementImportPage` / `CandidateReviewPage` / `CandidateRow`
- [x] Duplicate warnings on the receipt path too — `POST /transactions/from-receipt` now returns `ReceiptDraftDto`, and capture stops for confirmation instead of walking straight into the split editor
- [x] Unit tests: parser, classifier, dedup edge cases (50 passing, up from 26)
- [ ] Verify on Beta with a real CSV export *(user action)*

## Phase 5 — PDF statement parsing *(highest risk — prototype first)*

- [ ] **Prototype against one real PDF statement before building anything around it**
- [ ] `TextractDocumentClient` (TABLES), `bedrock:InvokeModel` IAM grant
- [ ] `BedrockClient.normalizeStatementRows` — Converse, JSON-schema output, temperature 0
- [ ] `PdfStatementParser` behind the existing `StatementParser` interface
- [ ] Partial-parse handling: report dropped row counts rather than failing the import
- [ ] Unit tests against recorded Textract output with `BedrockClient` stubbed (never call Bedrock in CI)
- [ ] Verify on Beta with a real PDF statement

## Phase 6 — Reimbursements

- [x] `ReimbursementsPage` — filtered ledger view *(shipped early, with Phase 3)*
- [x] `generateReimbursementSummary` + export action (FR23) *(shipped early, with Phase 3; unit-tested)*
- [ ] Verify the Uber/transit path end to end on Beta *(user action)*

## Phase 6a — Manual entry *(added after Phase 6; not in the original plan)*

Added because `ReceiptCapturePage` promised "add a transaction by hand" and no such path existed. The API side (`POST /transactions`) already existed — it is how statement candidates are promoted — so only the UI was missing.

- [x] `ManualEntryPage` at `/transactions/new`; entry points from the receipt page and the empty ledger
- [x] `apiClient.transactions.create` + `useCreateTransaction`
- [x] `TransactionService.create` now opens a REIMBURSEMENT rather than leaving it a DRAFT — the rule used to live only in the statement path, so a hand-entered claim would have been stranded (DRAFT exits only via `finalizeSplit`, which refuses non-SPLIT). `StatementIngestionService` lost its now-redundant status update.
- [x] `TransactionServiceCreateTest` pins that invariant, since two entry paths now share it (55 tests passing)
- [x] LLD §8.1/§8.2 updated with the route and why it is an escape hatch rather than a main path

## Phase 7 — Wrap-up

- [ ] Full manual Beta checklist (LLD §11) in one pass *(user action; deferred until all phases are built)*
- [ ] Confirm the statements bucket actually empties; receipt images retained *(needs a real import first)*
- [ ] CloudWatch logs/alarms sanity check *(needs traffic from the verification pass)*
- [x] Confirm no secrets in git history — scanned all 25 commits for token/key patterns and ever-committed credential files; the only hit, `SecretsConfig.java`, referenced a Secrets Manager *secret name*, never a value
- [ ] Rotate the GitHub PAT shared earlier in the project *(user action — cannot be done from here)*
- [x] README: live Beta URL, login notes, runbook

---

## Known issues

- **Pushes do not start a pipeline run — cause found, fix is a console action.** The AWS Connector for GitHub is present as an *authorized OAuth app* but was never *installed* as a GitHub App on the account. Those are different grants: the OAuth authorization lets the connection read the repo (which is why every manual run's Source stage succeeds), while the App installation is what delivers push events to AWS. Without it, `DetectChanges` has nothing to listen to. Ruled out along the way: `DetectChanges: "true"` **is** in the deployed source action, the connection reads `AVAILABLE`, and the empty `list-webhooks` is correct for a V1 connection-based source. Fix: AWS Console → Developer Tools → Settings → Connections → `split-manager-github` → install the app for the repo. Do it from the AWS side so the installation binds to the existing connection and the ARN in `pipeline-stack.ts` stays valid. Until then, start runs with `aws codepipeline start-pipeline-execution --name split-manager-pipeline`.
  - *The `triggerOnPush` change was still a real fix* — CDK omits `DetectChanges` from the template entirely when it is left unset — it just was not this problem.
  - A run that changes the pipeline self-mutates at `UpdatePipeline` and restarts as a **new execution**, cancelling the one you started. Expected, not a failure.
- **Issuer sign conventions are unverified against real exports.** The profiles were written from documented formats, not from files. If an import comes back with everything counted as a credit (or with nothing at all), that profile's `debitsArePositive` is inverted — a one-line fix in `issuer-profiles.yml`. A profile whose columns do not match the uploaded file is dropped entirely and header inference takes over, so picking the wrong issuer degrades rather than corrupts.
- ~~**Orphaned v1 table.**~~ Resolved — `list-tables` now returns only `split-manager-beta-ledger` and `split-manager-prod-ledger`, so the `-receipt-sessions` tables are already gone.
- A newly-typed person cannot be chosen as the payer until after one finalize — they have no id until the API creates them. `PayerSelector` says so rather than hiding them.

## Deferred (revisit only if needed)

- Reinstating `integ-tests/` and the Prod promotion gate — required before Prod becomes load-bearing
- `lambdas/` — only if PDF parsing latency exceeds the ALB idle timeout
- Receipt QR decoding, email receipt ingestion, parse-confidence surfacing, spending reports (BRD "Future scope")

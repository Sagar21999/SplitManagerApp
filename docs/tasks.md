# Split Manager — Implementation Task List

*Derived from `brd.md`, `hld.md`, `lld.md`, and `loe.md`. Tracks concrete, checkable implementation tasks in build order. Repo currently contains only `docs/` — no code has been written yet, so this starts from Phase 0.*

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done

*Update: Splitwise's public API is no longer available. Direct-to-Splitwise submission has been replaced with a manual handoff — the API computes the split and returns a shareable summary for the user to copy/paste into Splitwise (or anywhere else). See `brd.md`/`hld.md`/`lld.md`/`loe.md` for the full revision.*

## Phase 0 — Setup & prerequisites

- [x] Confirm AWS account access, configure local credentials
- [x] Create the GitHub monorepo structure (`infra/`, `frontend/`, `api/`, `lambdas/`, `integ-tests/`)
- [x] Set up local dev environment: Java 21 + Maven, Node + npm, AWS CDK CLI, Docker, DynamoDB Local

## Phase 1 — `infra/` (CDK, TypeScript)

- [x] Scaffold CDK app: `bin/infra.ts`, `PipelineStack`, `AppStage` (`envName: 'beta' | 'prod'`)
- [x] `DataStack`: DynamoDB `TableV2` (`split-manager-{env}-receipt-sessions`, PK `sessionId`, TTL on `expiresAt`) + S3 bucket for receipt images with lifecycle rule
- [x] `ApiStack`: ECS Fargate service + ALB + task IAM role scoped to DynamoDB table, S3 object read/write, `textract:AnalyzeExpense`
- [x] `FrontendStack`: S3 bucket + CloudFront distribution
- [x] `LambdaStack`: placeholder construct, no resources (P0 reserved only)
- [x] `cdk bootstrap` + first deploy; complete CodeStarConnections GitHub OAuth handshake (manual, AWS Console)
- [x] Verify pipeline end-to-end on minimal/empty stacks; debug first-deploy issues

## Phase 2 — `api/` (Java / Spring Boot / Maven)

- [x] Spring Boot + Maven skeleton, `Dockerfile`, package structure per LLD §3 (`controller/`, `service/`, `repository/`, `model/`, `dto/`, `client/`, `config/`, `exception/`)
- [x] Stub controllers + DTOs: `ReceiptController`, `SessionController`, `ExpenseController`
- [x] `TextractClient` (wraps `AnalyzeExpense`, field/line-item extraction helpers) + `ReceiptParsingService`
- [x] `ReceiptSessionRepository` (DynamoDB Enhanced Client, `@DynamoDbBean`) + `ReceiptSessionService`
- [x] `SplitCalculationService`: `computeEqualSplit` and `computeItemSplit` per LLD §5 algorithms, including rounding-remainder handling
- [x] `SplitSummaryService`: per-person breakdown + shareable text summary (replaces the removed `SplitwiseClient`/`SplitwiseRequestBuilder`/`SplitwiseService` — Splitwise's API is no longer available)
- [~] CORS config (restricted to CloudFront domain) — mechanism in place via `FRONTEND_ORIGIN` env var, still defaults to `*` until Phase 3 wires the real CloudFront domain into `ApiStack`
- [x] Implement `/parse-receipt`, `/session/{sessionId}`, `/finalize-split` per LLD §4 contracts (`/friends` and `/internal/submit-expense-dry-run` removed along with the Splitwise integration)
- [x] `GlobalExceptionHandler`, `SessionNotFoundException`
- [x] Deploy to Beta, manual smoke test, debug — verified live: `/actuator/health`, `GET /session/{id}`, `POST /finalize-split` all confirmed working against deployed Beta

## Phase 3 — `frontend/` (React + TypeScript)

- [x] Vite + React + TS scaffold, single route `/split/:sessionId`, `apiClient.ts`
- [x] `useReceiptSession` hook (calls `GET /session/{sessionId}` on mount)
- [x] `ReceiptReviewSection` (`ReceiptItemRow`, `AddItemButton`) — editable name/price, add/remove items
- [x] `TipEntrySection` (18/20/25% presets + manual numeric input)
- [x] `ParticipantsSection` (`ParticipantNameEntry`) — free-text add/remove participant names, no external contacts lookup
- [x] `SplitModeToggle` (Equal | By Item) + `ItemAssignmentGrid`/`ItemAssignmentRow` (tap-to-assign, By Item only)
- [x] `SplitSummary` — client-side preview mirroring `SplitCalculationService` logic (LLD §5), read-only
- [x] `ConfirmButton` + `ConfirmationModal` + finalize flow (`POST /finalize-split`), showing the shareable summary text with a copy action
- [x] Mobile-responsive styling pass (mobile web view — in-Shortcut framing dropped along with the Shortcut itself)
- [x] Deploy to Beta, manual smoke test, debug — build/synth verified; full click-through pending the user's own browser test alongside a planned UI change

## Phase 4 — Web upload & end-to-end integration

*Update: replaced the iOS Shortcut with a simple web upload page — no native app, no cost, no platform dependency. Reuses the same React app instead of a separate capture mechanism.*

- [x] `UploadPage` (`/upload`, also served at `/`): `<input type="file" accept="image/*" capture="environment">` (opens camera/library on mobile), `POST /parse-receipt` via `apiClient.parseReceipt`, navigates to `/split/{sessionId}` on success
- [x] Bumped API's multipart limits (`spring.servlet.multipart.max-file-size`/`max-request-size` to 10MB) — the Spring Boot default (1MB) would reject real phone photos
- [ ] End-to-end manual testing against Beta with real receipts (multiple iterations) — pending, alongside the user's planned UI change
- [ ] Bug-fixing pass across API/frontend from e2e findings

## Phase 5 — Automated testing

- [x] Unit tests: `SplitCalculationService` edge cases (single item, uneven sharers, rounding remainder, zero tip) — `api/src/test/java/.../SplitCalculationServiceTest.java`
- [x] Unit tests: `SplitSummaryService` (breakdown correctness, text formatting) — `api/src/test/java/.../SplitSummaryServiceTest.java`
- [x] `integ-tests/` package scaffold (JUnit + REST Assured)
- [x] `ParseReceiptIntegTest`, `SessionIntegTest`, `FinalizeSplitIntegTest` — chain off a bundled synthetic sample receipt image (`src/test/resources/sample-receipt.jpg`); not yet run against live Beta (costs a real Textract call per run — next actual pipeline deploy will exercise them)
- [x] Wire `IntegTests` `CodeBuildStep` into `betaStage.addPost(...)`; verify a failure actually blocks Prod promotion — already wired, and proven true this session (the missing-pom and wrong-Java-version failures both correctly blocked Prod until fixed)

## Phase 6 — Launch

- [ ] First automatic promotion to Prod; verify deployed stack
- [ ] Point the upload page at the Prod API URL; final real-world test
- [ ] Verify DynamoDB TTL cleanup and S3 lifecycle rule actually reclaim storage
- [ ] CloudWatch logs/alarms sanity check
- [ ] Rotate the GitHub PAT shared earlier in the project; confirm no secrets in git history
- [ ] Documentation wrap-up (live URLs, runbook notes)

## Sequencing notes (from `loe.md`)

- Phases 1–3 can start in parallel after Phase 0; each has an independent skeleton milestone.
- Phase 4 needs all three of `infra/`, `api/`, `frontend/` deployed to Beta first.
- Phase 5's `integ-tests/` depends on `POST /finalize-split` existing in `api/` (Phase 2), so it naturally follows.
- Phase 6 depends on everything above being green.

## Explicitly out of scope for this task list

Per BRD non-goals / HLD-LLD deferrals: `lambdas/` package internals, any auth/authz layer, percentage-split mode, automatic posting to Splitwise or any other third-party service, permanent history/reporting, bank/statement reconciliation.

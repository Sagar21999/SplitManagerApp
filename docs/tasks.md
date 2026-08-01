# Split Manager — Implementation Task List

*Derived from `brd.md`, `hld.md`, `lld.md`, and `loe.md`. Tracks concrete, checkable implementation tasks in build order. Repo currently contains only `docs/` — no code has been written yet, so this starts from Phase 0.*

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done

## Phase 0 — Setup & prerequisites

- [x] Confirm AWS account access, configure local credentials
- [x] Create the GitHub monorepo structure (`infra/`, `frontend/`, `api/`, `lambdas/`, `integ-tests/`)
- [x] Register Splitwise app; create a dummy test contact/group for dev use
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
- [x] Stub controllers + DTOs: `ReceiptController`, `SessionController`, `FriendsController`, `ExpenseController`
- [x] `TextractClient` (wraps `AnalyzeExpense`, field/line-item extraction helpers) + `ReceiptParsingService`
- [x] `ReceiptSessionRepository` (DynamoDB Enhanced Client, `@DynamoDbBean`) + `ReceiptSessionService`
- [x] `SplitCalculationService`: `computeEqualSplit` and `computeItemSplit` per LLD §5 algorithms, including rounding-remainder handling
- [x] `SplitwiseClient` interface + `SplitwiseHttpClient` (multipart/form-data to `secure.splitwise.com/api/v3.0`) + `SplitwiseRequestBuilder` (pure, side-effect-free) + `SplitwiseService`
- [~] CORS config (restricted to CloudFront domain) + Secrets Manager wiring for the Splitwise API key (CORS mechanism in place via `FRONTEND_ORIGIN` env var, still defaults to `*` until Phase 3 wires the real CloudFront domain into `ApiStack`; Secrets Manager wiring done)
- [x] Implement `/parse-receipt`, `/session/{sessionId}`, `/friends`, `/submit-expense`, `/internal/submit-expense-dry-run` per LLD §4 contracts
- [x] `GlobalExceptionHandler`, `SessionNotFoundException`, `SplitwiseApiException`
- [ ] Deploy to Beta, manual smoke test, debug

## Phase 3 — `frontend/` (React + TypeScript)

- [ ] Vite + React + TS scaffold, single route `/split/:sessionId`, `apiClient.ts`
- [ ] `useReceiptSession` hook (calls `GET /session/{sessionId}` on mount)
- [ ] `ReceiptReviewSection` (`ReceiptItemRow`, `AddItemButton`) — editable name/price, add/remove items
- [ ] `TipEntrySection` (18/20/25% presets + manual numeric input)
- [ ] `ParticipantsSection` (`GroupSelector`, `FriendMultiSelect`) wired to `GET /friends`
- [ ] `SplitModeToggle` (Equal | By Item) + `ItemAssignmentGrid`/`ItemAssignmentRow` (tap-to-assign, By Item only)
- [ ] `SplitSummary` — client-side preview mirroring `SplitCalculationService` logic (LLD §5), read-only
- [ ] `ConfirmButton` + `ConfirmationModal` + submit flow (`POST /submit-expense`)
- [ ] Mobile-responsive styling pass (in-Shortcut web view on a phone)
- [ ] Deploy to Beta, manual smoke test, debug

## Phase 4 — iOS Shortcut & end-to-end integration

- [ ] Build Shortcut: share-sheet trigger, HEIC→JPEG conversion, `POST /parse-receipt`, open returned URL in web view
- [ ] End-to-end manual testing against Beta with real receipts (multiple iterations)
- [ ] Bug-fixing pass across API/frontend from e2e findings

## Phase 5 — Automated testing

- [ ] Unit tests: `SplitCalculationService` edge cases (single item, uneven sharers, rounding remainder, zero tip)
- [ ] Unit tests: `SplitwiseRequestBuilder`
- [ ] `integ-tests/` package scaffold (JUnit + REST Assured)
- [ ] `ParseReceiptIntegTest`, `SessionIntegTest`, `FriendsIntegTest`
- [ ] `/internal/submit-expense-dry-run` endpoint (if not already done in Phase 2) + `SubmitExpenseDryRunIntegTest`
- [ ] Wire `IntegTests` `CodeBuildStep` into `betaStage.addPost(...)`; verify a failure actually blocks Prod promotion
- [ ] Manual live `create_expense` smoke test against the Splitwise dummy account (occasional, not automated)

## Phase 6 — Launch

- [ ] First automatic promotion to Prod; verify deployed stack
- [ ] Point the iOS Shortcut at the Prod API URL; final real-world test
- [ ] Verify DynamoDB TTL cleanup and S3 lifecycle rule actually reclaim storage
- [ ] CloudWatch logs/alarms sanity check
- [ ] Rotate the GitHub PAT shared earlier in the project; confirm no secrets in git history
- [ ] Documentation wrap-up (live URLs, runbook notes)

## Sequencing notes (from `loe.md`)

- Phases 1–3 can start in parallel after Phase 0; each has an independent skeleton milestone.
- Phase 4 needs all three of `infra/`, `api/`, `frontend/` deployed to Beta first.
- Phase 5's `integ-tests/` depends on `/internal/submit-expense-dry-run` existing in `api/` (Phase 2), so it naturally follows.
- Phase 6 depends on everything above being green.

## Explicitly out of scope for this task list

Per BRD non-goals / HLD-LLD deferrals: `lambdas/` package internals, any auth/authz layer, percentage-split mode, Splitwise rate-limit retry/queue logic, permanent history/reporting, bank/statement reconciliation.

# Split Manager — Low-Level Design (LLD)

*Companion documents: `brd.md` (business "why") and `hld.md` (architecture). This document expands the HLD into the engineering detail needed right before coding begins: exact class names and method signatures, database schemas with data types, algorithms, and UI component structure. If anything here conflicts with the HLD, the HLD's architecture wins; if anything here conflicts with the BRD's requirements, the BRD wins.*

*Note: while writing this document, two gaps in the earlier design surfaced and are resolved directly below rather than left open — (1) the frontend previously had no defined way to fetch a session's parsed data when the split page loads (the earlier `POST /parse-receipt` response is consumed by the Shortcut, not the browser) — resolved by adding `GET /session/{sessionId}`; (2) the exact mechanism for how `integ-tests/` stubs the Splitwise call was previously described only as "stubbed" without a concrete implementation — resolved by extracting request-building into a pure, independently-callable component and a dedicated dry-run endpoint (see "Integ-test stubbing mechanism" below).*

## 1. Repository layout

```
split-manager/
  infra/          # CDK app, TypeScript
  frontend/       # React + TypeScript SPA
  api/            # Java / Spring Boot service
  lambdas/        # Java, reserved, not built for P0
  integ-tests/    # Java, runs post-Beta-deploy, gates Prod promotion
  README.md
```

## 2. `infra/` — CDK (TypeScript)

```
infra/
  bin/infra.ts
  lib/
    pipeline-stack.ts
    app-stage.ts
    constructs/
      data-stack.ts
      api-stack.ts
      frontend-stack.ts
      lambda-stack.ts
```

**Classes:**

- `PipelineStack extends Stack` (`lib/pipeline-stack.ts`)
  - `constructor(scope: Construct, id: string, props: StackProps)`
  - `private buildPipeline(): pipelines.CodePipeline`
  - Instantiates `AppStage` twice ("Beta", "Prod"); attaches the IntegTests `CodeBuildStep` via `betaStage.addPost(...)`.

- `AppStage extends Stage implements AppStageProps` (`lib/app-stage.ts`)
  - `constructor(scope: Construct, id: string, props: AppStageProps)`
  - `readonly dataStack: DataStack`
  - `readonly apiStack: ApiStack`
  - `readonly frontendStack: FrontendStack`
  - `readonly apiUrlOutput: CfnOutput` — the API ALB's DNS name, consumed by the IntegTests step and by the frontend build.
  - `interface AppStageProps { envName: 'beta' | 'prod' }`

- `DataStack extends Stack` (`lib/constructs/data-stack.ts`)
  - `constructor(scope, id, props: DataStackProps)`
  - `readonly table: dynamodb.TableV2`
  - `readonly imagesBucket: s3.Bucket`
  - `interface DataStackProps extends StackProps { envName: string }`

- `ApiStack extends Stack` (`lib/constructs/api-stack.ts`)
  - `constructor(scope, id, props: ApiStackProps)`
  - `readonly service: ecs.FargateService`
  - `readonly loadBalancer: elbv2.ApplicationLoadBalancer`
  - `readonly taskRole: iam.Role` — granted `dynamodb:*` (scoped to `table.tableArn`), `s3:GetObject`/`s3:PutObject` (scoped to `imagesBucket.arnForObjects('*')`), and `textract:AnalyzeExpense` (resource `"*"` — Textract does not support resource-level ARN scoping for this action).
  - `interface ApiStackProps extends StackProps { envName: string; table: dynamodb.ITable; imagesBucket: s3.IBucket }`

- `FrontendStack extends Stack` (`lib/constructs/frontend-stack.ts`)
  - `constructor(scope, id, props: FrontendStackProps)`
  - `readonly bucket: s3.Bucket`
  - `readonly distribution: cloudfront.Distribution`
  - `interface FrontendStackProps extends StackProps { envName: string; apiUrl: string }`

- `LambdaStack extends Stack` (`lib/constructs/lambda-stack.ts`) — placeholder, no resources defined until the Lambda package is actually needed.

**Pipeline post-deploy wiring:**

```ts
const betaStage = pipeline.addStage(new AppStage(this, "Beta", { envName: "beta" }));
betaStage.addPost(
  new CodeBuildStep("IntegTests", {
    commands: ["cd integ-tests", "mvn test -Dbeta.api.url=$BETA_API_URL"],
    envFromCfnOutputs: { BETA_API_URL: betaStage.apiUrlOutput },
  }),
);
const prodStage = pipeline.addStage(new AppStage(this, "Prod", { envName: "prod" }));
// No manual approval — promotion is automatic once IntegTests passes.
```

## 3. `api/` — Java / Spring Boot / Maven

**Package structure:** `com.splitmanager.api`

```
com.splitmanager.api
  controller/   ReceiptController, SessionController, FriendsController, ExpenseController
  service/      ReceiptParsingService, SplitCalculationService, SplitwiseService, ReceiptSessionService
  repository/   ReceiptSessionRepository
  model/        ReceiptSession, ReceiptItem, FinalizedSplit, SplitMode, SplitwiseFriend, SplitwiseGroup, SessionStatus
  dto/          ParseReceiptResponse, SessionResponse, FriendsResponse, SubmitExpenseRequest, SubmitExpenseResponse
  client/       TextractClient, SplitwiseClient (interface), SplitwiseHttpClient, SplitwiseRequestBuilder
  config/       AwsClientConfig, WebConfig, SecretsConfig
  exception/    SessionNotFoundException, SplitwiseApiException, GlobalExceptionHandler
```

### 3.1 Controllers

```java
@RestController
class ReceiptController {
  @PostMapping("/parse-receipt")
  ResponseEntity<ParseReceiptResponse> parseReceipt(@RequestParam("image") MultipartFile image);
}

@RestController
class SessionController {
  @GetMapping("/session/{sessionId}")
  ResponseEntity<SessionResponse> getSession(@PathVariable String sessionId);
}

@RestController
class FriendsController {
  @GetMapping("/friends")
  ResponseEntity<FriendsResponse> getFriends();
}

@RestController
class ExpenseController {
  @PostMapping("/submit-expense")
  ResponseEntity<SubmitExpenseResponse> submitExpense(@RequestBody SubmitExpenseRequest request);

  // Internal-only: builds the Splitwise request without sending it, so integ-tests
  // can verify request-construction without creating a real Splitwise expense.
  // See "Integ-test stubbing mechanism" below.
  @PostMapping("/internal/submit-expense-dry-run")
  ResponseEntity<SplitwiseExpenseRequestDto> submitExpenseDryRun(@RequestBody SubmitExpenseRequest request);
}
```

### 3.2 Services

```java
class ReceiptParsingService {
  ParsedReceipt parse(byte[] imageBytes, String contentType);
}

class SplitCalculationService {
  FinalizedSplit computeEqualSplit(BigDecimal total, List<String> participantIds);
  FinalizedSplit computeItemSplit(
      List<ReceiptItem> items,
      Map<String, List<String>> itemAssignments, // itemId -> participantIds sharing it
      BigDecimal tax,
      BigDecimal tip,
      String payerId);

  // private helpers
  private Map<String, BigDecimal> subtotalsByParticipant(List<ReceiptItem> items, Map<String, List<String>> assignments);
  private Map<String, BigDecimal> prorate(BigDecimal amount, Map<String, BigDecimal> subtotals, BigDecimal totalSubtotal);
  private void applyRoundingRemainder(Map<String, BigDecimal> shares, BigDecimal target, String payerId);
}

class ReceiptSessionService {
  ReceiptSession create(String imageS3Key, String contentType);
  ReceiptSession get(String sessionId); // throws SessionNotFoundException
  void updateParsedFields(String sessionId, ParsedReceipt parsed);
  void markSubmitted(String sessionId, String splitwiseExpenseId);
  void markFailed(String sessionId, String reason);
}

class SplitwiseService {
  SplitwiseFriendsAndGroups getFriendsAndGroups();
  String createExpense(ReceiptSession session, FinalizedSplit split, byte[] imageBytes); // returns splitwiseExpenseId
}
```

### 3.3 Clients

```java
class TextractClient {
  ExpenseDocument analyzeExpense(byte[] imageBytes); // wraps software.amazon.awssdk.services.textract
  // mapping helpers
  Optional<String> extractSummaryField(ExpenseDocument doc, String fieldType); // e.g. "VENDOR_NAME", "TOTAL", "TAX"
  List<ReceiptItem> extractLineItems(ExpenseDocument doc);
}

interface SplitwiseClient {
  List<SplitwiseFriend> getFriends();
  List<SplitwiseGroup> getGroups();
  String createExpense(SplitwiseExpenseRequestDto request); // returns Splitwise expense ID
}

// Production implementation — real HTTP calls to secure.splitwise.com/api/v3.0.
class SplitwiseHttpClient implements SplitwiseClient { ... }

// Pure, side-effect-free request construction — used by both the real submit-expense
// flow and the dry-run endpoint, and unit-testable with no HTTP involved at all.
class SplitwiseRequestBuilder {
  SplitwiseExpenseRequestDto build(ReceiptSession session, FinalizedSplit split);
  // Builds the multipart/form-data shape: per-user paid_share/owed_share fields,
  // cost = sum(owed_share), description = merchant, receipt image attached by S3 key reference.
}
```

### 3.4 Models

```java
class ReceiptSession {
  String sessionId;                 // partition key, UUID
  String userId;                    // constant "single-user" for P0
  SessionStatus status;             // enum below
  Instant createdAt;
  long expiresAt;                   // epoch seconds, DynamoDB TTL attribute
  String receiptImageS3Key;
  String receiptImageContentType;
  String merchant;                  // nullable
  List<ReceiptItem> items;
  BigDecimal tax;                   // nullable
  BigDecimal tip;                   // nullable
  BigDecimal total;                 // nullable
  FinalizedSplit finalizedSplit;    // nullable, written right before submit
  String splitwiseExpenseId;        // nullable
  String failureReason;             // nullable
}

enum SessionStatus { PARSING, PARSED, PARSE_FAILED, SUBMITTED, SUBMIT_FAILED }

class ReceiptItem {
  String id;         // UUID, client-generated on add, server-generated on parse
  String name;
  BigDecimal price;
}

enum SplitMode { EQUAL, BY_ITEM }

class FinalizedSplit {
  SplitMode mode;
  Map<String, BigDecimal> participantShares; // participantId -> owed amount
  String payerId;                             // who fronted the bill; receives rounding remainder
}
```

### 3.5 DynamoDB schema

Table name: `split-manager-{env}-receipt-sessions` (e.g. `split-manager-beta-receipt-sessions`).

| Attribute | DynamoDB type | Notes |
|---|---|---|
| `sessionId` | S (String) | Partition key. UUID v4. |
| `userId` | S | Constant `"single-user"` for P0. |
| `status` | S | One of `PARSING`, `PARSED`, `PARSE_FAILED`, `SUBMITTED`, `SUBMIT_FAILED`. |
| `createdAt` | N (Number) | Epoch seconds. |
| `expiresAt` | N | Epoch seconds. **TTL attribute** — DynamoDB auto-deletes the item after this time. |
| `receiptImageS3Key` | S | |
| `receiptImageContentType` | S | e.g. `image/jpeg`. |
| `merchant` | S | Nullable/absent if not parsed. |
| `items` | L (List) of M (Map) | Each element: `{ id: S, name: S, price: N }`. |
| `tax` | N | Nullable/absent. |
| `tip` | N | Nullable/absent. |
| `total` | N | Nullable/absent. |
| `finalizedSplit` | M | Nullable/absent until submit. Shape: `{ mode: S, participantShares: M<S,N>, payerId: S }`. |
| `splitwiseExpenseId` | S | Nullable/absent until submit succeeds. |
| `failureReason` | S | Nullable/absent. |

No secondary indexes — every access pattern for P0 is a direct `GetItem`/`PutItem`/`UpdateItem` by `sessionId`. Accessed via the AWS SDK for Java v2's DynamoDB Enhanced Client (`software.amazon.awssdk:dynamodb-enhanced`), with `ReceiptSession` annotated as a `@DynamoDbBean`.

```java
class ReceiptSessionRepository {
  void save(ReceiptSession session);
  Optional<ReceiptSession> findById(String sessionId);
  void update(ReceiptSession session); // full-item overwrite via PutItem for P0 simplicity
}
```

## 4. API contracts

### `POST /parse-receipt`
- Body: `multipart/form-data`, field `image`.
- Response `200`:
```json
{
  "sessionId": "string",
  "merchant": "string | null",
  "items": [{ "id": "string", "name": "string", "price": 0 }],
  "tax": 0,
  "tip": null,
  "total": 0,
  "url": "https://.../split/{sessionId}"
}
```

### `GET /session/{sessionId}` *(new — closes the frontend-data-loading gap)*
- Called by the frontend's `SplitPage` on mount, since the browser navigating to the `url` from `/parse-receipt` never receives that response body directly.
- Response `200`: same shape as `ParseReceiptResponse` minus `url`, plus `status`.
- Response `404` if the session has expired or never existed.

### `GET /friends`
```json
{
  "friends": [{ "splitwiseId": 0, "firstName": "string", "lastName": "string", "avatarUrl": "string" }],
  "groups": [{ "id": 0, "name": "string", "members": [ /* SplitParticipant[] */ ] }]
}
```

### `POST /submit-expense`
- Body: `{ "sessionId": "string", "split": { "mode": "EQUAL"|"BY_ITEM", "participantShares": {"participantId": 0}, "payerId": "string" } }`
- Response `200`: `{ "success": true, "splitwiseExpenseId": "string" }` or `{ "success": false, "error": "string" }`. Idempotent on an already-`SUBMITTED` session.

### `POST /internal/submit-expense-dry-run` *(new — integ-test support)*
- Same request body as `/submit-expense`.
- Runs `SplitCalculationService` + `SplitwiseRequestBuilder` only — never calls `SplitwiseClient`, never touches Splitwise, never updates the session's status.
- Response `200`: the constructed `SplitwiseExpenseRequestDto` (the exact `paid_share`/`owed_share`/`cost` fields that *would* have been sent), for the integ test to assert against.

## 5. Split-calculation algorithm

**Equal split** — `SplitCalculationService.computeEqualSplit`:

```
n = participantIds.size()
baseShare = roundDown(total / n, 2 decimal places)
shares = { id: baseShare for id in participantIds }
remainderCents = round((total - baseShare * n) * 100)   // leftover pennies, always >= 0 and < n
for i in 0 until remainderCents:
    shares[participantIds[i]] += 0.01                    // distribute one cent at a time, in list order
return FinalizedSplit(EQUAL, shares, payerId = null)
```

**By-item split** — `SplitCalculationService.computeItemSplit`:

```
subtotal = sum(item.price for item in items)
participantSubtotals = {}                                 // participantId -> BigDecimal, starts at 0
for item in items:
    sharers = itemAssignments[item.id]
    perPersonShare = item.price / sharers.size()           // BigDecimal division, HALF_UP, unrounded intermediate
    for p in sharers:
        participantSubtotals[p] += perPersonShare

owedShare = {}
for p, subtotalP in participantSubtotals:
    proportion = subtotalP / subtotal
    taxShare = tax * proportion
    tipShare = tip * proportion
    owedShare[p] = round(subtotalP + taxShare + tipShare, 2, HALF_UP)

totalOwed = sum(owedShare.values())
remainder = total - totalOwed                              // rounding drift, typically +/- a cent or two
owedShare[payerId] += remainder                             // the payer absorbs the rounding remainder
return FinalizedSplit(BY_ITEM, owedShare, payerId)
```

Both algorithms guarantee `sum(shares.values()) == total` exactly, which is required before `SplitwiseRequestBuilder` builds the request (Splitwise's `create_expense` expects `cost` to equal the sum of `owed_share` across participants).

## 6. `frontend/` — React + TypeScript

**Routing:** single route, `/split/:sessionId` (`SplitPage`) — the Shortcut always deep-links directly here; there is no separate landing page for P0.

**Component tree:**

```
App
 └─ SplitPage (route: /split/:sessionId)
     ├─ ReceiptReviewSection
     │   ├─ ReceiptItemRow          (repeated, one per item — editable name/price)
     │   └─ AddItemButton
     ├─ TipEntrySection
     │   └─ TipPresetButtons (18/20/25%) + manual numeric input
     ├─ ParticipantsSection
     │   ├─ GroupSelector           (dropdown of Splitwise groups)
     │   └─ FriendMultiSelect       (checkboxes, individual friends)
     ├─ SplitModeToggle             (Equal | By Item)
     ├─ ItemAssignmentGrid          (rendered only in By Item mode)
     │   └─ ItemAssignmentRow       (per item: a checkbox per participant)
     ├─ SplitSummary                (computed per-person totals, read-only preview)
     ├─ ConfirmButton
     └─ ConfirmationModal           (success/failure state, terminal)
```

**Key component props (TypeScript):**

```ts
interface ReceiptItemRowProps {
  item: ReceiptItem;
  onChange: (updated: ReceiptItem) => void;
  onRemove: (id: string) => void;
}

interface ItemAssignmentGridProps {
  items: ReceiptItem[];
  participants: Participant[];
  assignments: Record<string, string[]>; // itemId -> participantIds sharing it
  onAssignmentChange: (itemId: string, participantIds: string[]) => void;
}

interface SplitSummaryProps {
  participants: Participant[];
  shares: Record<string, number>; // participantId -> computed owed amount
}
```

**State management:** local component state only (`useState`/`useReducer` within `SplitPage`) — no global store, since this is a single page driven by a single session with no cross-page state to share. A custom hook loads the session on mount:

```ts
function useReceiptSession(sessionId: string): {
  session: SessionResponse | null;
  loading: boolean;
  error: string | null;
} // calls GET /session/{sessionId} internally
```

**API client (`apiClient.ts`):**

```ts
function getSession(sessionId: string): Promise<SessionResponse>;
function getFriends(): Promise<FriendsResponse>;
function submitExpense(request: SubmitExpenseRequest): Promise<SubmitExpenseResponse>;
```

Split totals shown in `SplitSummary` are computed client-side, mirroring the API's `SplitCalculationService` logic (see Section 5) purely for live preview as the user adjusts assignments — the API recomputes and is the source of truth at submit time; the frontend never sends pre-computed shares that the API doesn't independently verify.

## 7. `integ-tests/` — Java, JUnit + REST Assured

**Package:** `com.splitmanager.integtests`

```java
class ParseReceiptIntegTest {
  @Test void parseReceiptReturnsStructuredFields();       // calls live Beta /parse-receipt with a known sample image
}

class SessionIntegTest {
  @Test void getSessionReturnsPreviouslyParsedData();     // chains off a parse-receipt call
}

class FriendsIntegTest {
  @Test void getFriendsReturnsListFromLiveSplitwise();    // calls live Beta /friends (Splitwise read call, no rate-limit concern)
}

class SubmitExpenseDryRunIntegTest {
  @Test void dryRunBuildsCorrectSplitwiseRequest();        // calls live Beta /internal/submit-expense-dry-run
                                                             // asserts paid_share/owed_share/cost fields, WITHOUT
                                                             // ever calling Splitwise's real create_expense
}
```

### Integ-test stubbing mechanism (resolves the earlier open item)

Rather than intercepting outbound HTTP from a running Beta service (impractical against an already-deployed ECS task) or hitting live Splitwise on every automatic pipeline run (risks the ~3-4/day free-tier cap), `submit-expense`'s request-construction logic (`SplitwiseRequestBuilder`) is a pure, side-effect-free component reachable two ways in production code:

1. `POST /submit-expense` — builds the request, then actually calls `SplitwiseClient.createExpense()`. Used by real app traffic.
2. `POST /internal/submit-expense-dry-run` — builds the request via the same `SplitwiseRequestBuilder`, returns it as JSON, and stops — never touches `SplitwiseClient`. Used only by `SubmitExpenseDryRunIntegTest`.

This keeps the stub entirely inside the already-deployed API (no separate mock server, no per-test environment spin-up) while guaranteeing the integ-test suite never creates a real Splitwise expense on an automatic pipeline run. An actual live `create_expense` smoke test against the Splitwise dummy account remains a manual, occasional check — not part of the automated suite.

## 8. `lambdas/`

Reserved package, no classes defined. Not built for P0 — see HLD/BRD for the conditions under which this would get used (a real latency/UX problem with synchronous parsing).

# Split Manager — Low-Level Design (LLD)

*Companion documents: `brd.md` (business "why") and `hld.md` (architecture). This document expands the HLD into the engineering detail needed right before coding begins: exact class names and method signatures, database schemas with data types, algorithms, and UI component structure. If anything here conflicts with the HLD, the HLD's architecture wins; if anything here conflicts with the BRD's requirements, the BRD wins.*

*Note: while writing this document, a gap in the earlier design surfaced and is resolved directly below rather than left open — the frontend previously had no defined way to fetch a session's parsed data when the split page loads (the earlier `POST /parse-receipt` response is consumed by the Shortcut, not the browser) — resolved by adding `GET /session/{sessionId}`.*

*Update: Splitwise's public API is no longer available. This document has been revised to replace direct-to-Splitwise submission with a "manual handoff" — the API computes the split and returns a shareable summary for the user to copy into Splitwise (or anywhere else) themselves. This removed the `SplitwiseClient`/`SplitwiseHttpClient`/`SplitwiseRequestBuilder`/`SplitwiseService` components, the `GET /friends` endpoint, and the `/internal/submit-expense-dry-run` dry-run mechanism (no longer needed — there's no external, rate-limited API call left to avoid in tests).*

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
  controller/   ReceiptController, SessionController, ExpenseController
  service/      ReceiptParsingService, SplitCalculationService, SplitSummaryService, ReceiptSessionService
  repository/   ReceiptSessionRepository
  model/        ReceiptSession, ReceiptItem, FinalizedSplit, SplitMode, SessionStatus
  dto/          ParseReceiptResponse, SessionResponse, SplitSummaryDto, SubmitExpenseRequest, FinalizeSplitResponse
  client/       TextractClient
  config/       AwsClientConfig, WebConfig
  exception/    SessionNotFoundException, GlobalExceptionHandler
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
class ExpenseController {
  @PostMapping("/finalize-split")
  ResponseEntity<FinalizeSplitResponse> finalizeSplit(@RequestBody SubmitExpenseRequest request);
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
  void markParseFailed(String sessionId, String reason);
  void markFinalized(String sessionId);
}

// Pure, side-effect-free — reuses the already-computed FinalizedSplit.
class SplitSummaryService {
  SplitSummaryDto generateSummary(ReceiptSession session, FinalizedSplit split);
  // Builds a structured per-person breakdown and a formatted, copy-pasteable
  // text block (merchant, per-person amounts) for manual handoff to Splitwise/
  // any other app.
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
  FinalizedSplit finalizedSplit;    // nullable, written right before finalize
  String failureReason;             // nullable
}

enum SessionStatus { PARSING, PARSED, PARSE_FAILED, FINALIZED }

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
| `status` | S | One of `PARSING`, `PARSED`, `PARSE_FAILED`, `FINALIZED`. |
| `createdAt` | N (Number) | Epoch seconds. |
| `expiresAt` | N | Epoch seconds. **TTL attribute** — DynamoDB auto-deletes the item after this time. |
| `receiptImageS3Key` | S | |
| `receiptImageContentType` | S | e.g. `image/jpeg`. |
| `merchant` | S | Nullable/absent if not parsed. |
| `items` | L (List) of M (Map) | Each element: `{ id: S, name: S, price: N }`. |
| `tax` | N | Nullable/absent. |
| `tip` | N | Nullable/absent. |
| `total` | N | Nullable/absent. |
| `finalizedSplit` | M | Nullable/absent until finalize. Shape: `{ mode: S, participantShares: M<S,N>, payerId: S }`. |
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

### `POST /finalize-split`
- Body: `{ "sessionId": "string", "split": { "mode": "EQUAL"|"BY_ITEM", "participantShares": {"participantId": 0}, "payerId": "string" } }`
- Response `200`: `{ "success": true, "summary": { "amountOwedByParticipant": {"participantId": 0}, "shareText": "string" }, "error": null }`.
- `shareText` is a formatted, copy-pasteable plain-text block (merchant, total, per-person amounts) for the user to paste into Splitwise or any other app. Idempotent on an already-`FINALIZED` session (regenerates the summary from the stored split rather than erroring).

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

Both algorithms guarantee `sum(shares.values()) == total` exactly, which `SplitSummaryService` relies on when building the per-person breakdown and share text.

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
     │   └─ ParticipantNameEntry    (free-text add/remove participant names)
     ├─ SplitModeToggle             (Equal | By Item)
     ├─ ItemAssignmentGrid          (rendered only in By Item mode)
     │   └─ ItemAssignmentRow       (per item: a checkbox per participant)
     ├─ SplitSummary                (computed per-person totals, read-only preview)
     ├─ ConfirmButton
     └─ ConfirmationModal           (terminal state: shows the shareable summary text + a copy action)
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
function finalizeSplit(request: SubmitExpenseRequest): Promise<FinalizeSplitResponse>;
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

class FinalizeSplitIntegTest {
  @Test void finalizeSplitReturnsCorrectSummary();         // calls live Beta /finalize-split, asserts
                                                             // amountOwedByParticipant and shareText
}
```

No external API is called by `POST /finalize-split` — `SplitSummaryService` is a pure, local computation over the already-parsed session and the submitted split — so there's no rate-limit concern and no need for a separate dry-run endpoint or stubbing mechanism; the integ test hits the real endpoint directly.

## 8. `lambdas/`

Reserved package, no classes defined. Not built for P0 — see HLD/BRD for the conditions under which this would get used (a real latency/UX problem with synchronous parsing).

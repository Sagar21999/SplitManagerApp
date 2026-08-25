# Split Manager — Low-Level Design (LLD)

*Companion documents: `brd.md` (business "why") and `hld.md` (architecture). This document expands the HLD into the engineering detail needed right before coding begins: exact class names and method signatures, database schemas with data types, algorithms, and UI component structure. If anything here conflicts with the HLD, the HLD's architecture wins; if anything here conflicts with the BRD's requirements, the BRD wins.*

*Revision note: this is v2, matching BRD v2 and HLD v2. The v1 design — transient `ReceiptSession` records with a TTL, no auth, two split modes — is preserved in git history at commit 6e814bb. Section 12 lists exactly what carries over from the existing build, what is modified, and what is deleted.*

## 1. Repository layout

```
split-manager/
  infra/          # CDK app, TypeScript
  frontend/       # React + TypeScript SPA
  api/            # Java / Spring Boot service
  lambdas/        # Java, reserved, still not built
  integ-tests/    # Java, runs post-Beta-deploy, gates Prod promotion
  docs/
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
      auth-stack.ts        # NEW
      data-stack.ts        # MODIFIED
      api-stack.ts         # MODIFIED
      frontend-stack.ts
      lambda-stack.ts
```

**`AuthStack extends Stack`** *(new)* — `lib/constructs/auth-stack.ts`

```ts
readonly userPool: cognito.UserPool;
readonly userPoolClient: cognito.UserPoolClient;
readonly userPoolDomain: cognito.UserPoolDomain;
interface AuthStackProps extends StackProps { envName: string; frontendUrl: string }
```

- `selfSignUpEnabled: false` — the single account is provisioned manually.
- `passwordPolicy`: 12-char minimum, all character classes.
- MFA optional (TOTP), `accountRecovery: EMAIL_ONLY`.
- Client is a **public** client (no secret), `authFlows: { userSrp: true }`, OAuth `authorizationCodeGrant` with PKCE, scopes `openid`/`email`/`profile`.
- Callback/logout URLs point at the CloudFront domain, plus `http://localhost:5173` in Beta only for local development.

**`DataStack`** *(modified)*

```ts
readonly table: dynamodb.TableV2;          // single table, renamed, no TTL
readonly receiptsBucket: s3.Bucket;        // retained images
readonly statementsBucket: s3.Bucket;      // NEW, aggressive lifecycle purge
```

- The table's TTL attribute is **removed**. Records are permanent.
- `pointInTimeRecovery: true` — this is now durable financial data.
- Two GSIs, defined in §4.2.
- `statementsBucket` has a 1-day lifecycle expiry as a backstop; the API deletes objects explicitly after extraction.

**`ApiStack`** *(modified)*

```ts
interface ApiStackProps extends StackProps {
  envName: string;
  table: dynamodb.ITable;
  receiptsBucket: s3.IBucket;
  statementsBucket: s3.IBucket;
  userPool: cognito.IUserPool;
  userPoolClient: cognito.IUserPoolClient;
}
```

Task role grants, over v1: adds `dynamodb:Query` on the table's index ARNs, `s3:DeleteObject` on the statements bucket, `textract:AnalyzeDocument`, and `bedrock:InvokeModel` scoped to the specific model ARN. Environment adds `COGNITO_ISSUER_URI` and `COGNITO_CLIENT_ID`.

**`FrontendStack`** — unchanged in shape; its build-time config now also carries the Cognito pool/client IDs and hosted-UI domain.

**Pipeline wiring** — unchanged from v1: `betaStage.addPost(new CodeBuildStep("IntegTests", ...))`, automatic Prod promotion on pass. The integ-test step gains a Cognito test-user credential from Secrets Manager (§11).

## 3. `api/` — Java / Spring Boot / Maven

**Package structure:** `com.splitmanager.api`

```
com.splitmanager.api
  controller/   TransactionController, StatementController, PersonController, BalanceController
  service/      ReceiptParsingService, SplitCalculationService, SplitSummaryService,
                TransactionService, PersonService, BalanceService,
                StatementIngestionService, StatementClassificationService, DeduplicationService
  repository/   TransactionRepository, PersonRepository, StatementImportRepository
  model/        Transaction, TransactionType, TransactionStatus, LineItem, Participant,
                SplitDefinition, SplitMode, FinalizedSplit, Person,
                StatementImport, StatementCandidate, CandidateClassification
  dto/          ...  (§4.3)
  client/       TextractExpenseClient, TextractDocumentClient, BedrockClient
  parser/       StatementParser (iface), CsvStatementParser, PdfStatementParser
  config/       AwsClientConfig, WebConfig, SecurityConfig
  exception/    TransactionNotFoundException, PersonNotFoundException,
                StatementParseException, GlobalExceptionHandler
```

### 3.1 Security configuration

```java
@Configuration
@EnableWebSecurity
class SecurityConfig {
  @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a
            .requestMatchers("/actuator/health").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
        .csrf(CsrfConfigurer::disable)          // stateless bearer-token API
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS));
    return http.build();
  }
}
```

`spring.security.oauth2.resourceserver.jwt.issuer-uri` points at the Cognito pool; Spring fetches and caches the JWKS and validates signature, issuer, and expiry. `audience` is validated against `COGNITO_CLIENT_ID` via a custom `OAuth2TokenValidator`. Dependency: `spring-boot-starter-oauth2-resource-server`.

`/actuator/health` stays public so the ALB health check works.

### 3.2 Controllers

```java
@RestController @RequestMapping("/transactions")
class TransactionController {
  @PostMapping("/from-receipt")
  ResponseEntity<TransactionDto> createFromReceipt(@RequestParam("image") MultipartFile image);

  @PostMapping                     ResponseEntity<TransactionDto> create(@RequestBody CreateTransactionRequest r);
  @GetMapping                      ResponseEntity<TransactionListDto> list(
                                       @RequestParam(required=false) TransactionStatus status,
                                       @RequestParam(required=false) TransactionType type,
                                       @RequestParam(required=false) String cursor,
                                       @RequestParam(defaultValue="50") int limit);
  @GetMapping("/{id}")             ResponseEntity<TransactionDetailDto> get(@PathVariable String id);
  @PutMapping("/{id}")             ResponseEntity<TransactionDto> update(@PathVariable String id,
                                                                         @RequestBody UpdateTransactionRequest r);
  @PostMapping("/{id}/finalize")   ResponseEntity<TransactionDetailDto> finalize(@PathVariable String id,
                                                                                @RequestBody FinalizeRequest r);
  @PatchMapping("/{id}/status")    ResponseEntity<TransactionDto> updateStatus(@PathVariable String id,
                                                                              @RequestBody StatusUpdateRequest r);
  @DeleteMapping("/{id}")          ResponseEntity<Void> delete(@PathVariable String id);
}

@RestController @RequestMapping("/statements")
class StatementController {
  @PostMapping                                     ResponseEntity<StatementImportDto> upload(
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(required=false) String issuerProfile);
  @GetMapping("/{id}/candidates")                  ResponseEntity<List<CandidateDto>> candidates(@PathVariable String id);
  @PostMapping("/{id}/candidates/{cid}/confirm")   ResponseEntity<TransactionDto> confirm(
                                                       @PathVariable String id, @PathVariable String cid,
                                                       @RequestBody ConfirmCandidateRequest r);
  @PostMapping("/{id}/candidates/{cid}/dismiss")   ResponseEntity<Void> dismiss(@PathVariable String id,
                                                                               @PathVariable String cid);
}

@RestController @RequestMapping("/people")
class PersonController {
  @GetMapping                ResponseEntity<List<PersonDto>> list();
  @PostMapping               ResponseEntity<PersonDto> create(@RequestBody CreatePersonRequest r);
  @PatchMapping("/{id}")     ResponseEntity<PersonDto> rename(@PathVariable String id, @RequestBody RenamePersonRequest r);
  @DeleteMapping("/{id}")    ResponseEntity<Void> delete(@PathVariable String id);
}

@RestController
class BalanceController {
  @GetMapping("/balances")   ResponseEntity<BalancesDto> balances();
}
```

### 3.3 Services

```java
class TransactionService {
  Transaction createFromReceipt(String imageS3Key, String contentType, ParsedReceipt parsed);
  Transaction create(CreateTransactionRequest r);
  Transaction get(String id);                       // throws TransactionNotFoundException
  Page<Transaction> list(TransactionStatus s, TransactionType t, String cursor, int limit);
  Transaction update(String id, UpdateTransactionRequest r);   // DRAFT only
  Transaction finalize(String id, SplitDefinition split);      // recomputes server-side, -> OPEN
  Transaction updateStatus(String id, TransactionStatus next); // validates the transition
  void delete(String id);
}

class PersonService {
  List<Person> list();
  Person create(String displayName);
  Person rename(String id, String displayName);
  void delete(String id);                            // soft-delete; historical transactions keep the name
  List<Person> resolveOrCreate(List<String> displayNames);   // called on finalize; implements BRD FR13
}

class BalanceService {
  // Aggregates OPEN + EXTERNALLY_ADDED transactions; SETTLED and DRAFT excluded.
  // Computed on read (HLD "Scalability"), not maintained as a counter.
  BalancesDto computeBalances();
}

class SplitCalculationService {
  FinalizedSplit compute(SplitDefinition definition, TransactionAmounts amounts, List<LineItem> items);

  // private — the unified pipeline of §5
  private Map<String, BigDecimal> resolveWeights(SplitDefinition d, List<LineItem> items);
  private Map<String, BigDecimal> distribute(BigDecimal total, Map<String, BigDecimal> weights);
  private void applyRoundingRemainder(Map<String, BigDecimal> shares, BigDecimal target, String payerId);
}

class SplitSummaryService {
  SplitSummaryDto generateSummary(Transaction txn);   // per-person breakdown + copy-pasteable text
  String generateReimbursementSummary(List<Transaction> reimbursements);  // BRD FR23
}

class StatementIngestionService {
  StatementImport ingest(MultipartFile file, String issuerProfile);
  // 1. store to statementsBucket  2. select parser by content type
  // 3. parse  4. classify  5. dedup-check  6. persist batch  7. DELETE the S3 object
}

class StatementClassificationService {
  CandidateClassification classify(RawStatementRow row);
}

class DeduplicationService {
  List<DuplicateMatch> findMatches(String merchant, LocalDate date, BigDecimal amount);
}
```

### 3.4 Clients and parsers

```java
class TextractExpenseClient {                        // carried over from v1, unchanged
  ExpenseDocument analyzeExpense(byte[] imageBytes);
  Optional<String> extractSummaryField(ExpenseDocument doc, String fieldType);
  List<LineItem> extractLineItems(ExpenseDocument doc);
}

class TextractDocumentClient {                       // NEW — PDF statements
  List<ExtractedTable> analyzeTables(byte[] pdfBytes);
}

class BedrockClient {                                // NEW
  List<RawStatementRow> normalizeStatementRows(List<ExtractedTable> tables);
  // Converse API, JSON-schema-constrained output, temperature 0.
}

interface StatementParser {
  boolean supports(String contentType);
  List<RawStatementRow> parse(byte[] bytes, String issuerProfile);
}

class CsvStatementParser implements StatementParser { /* deterministic, per-issuer column mapping */ }
class PdfStatementParser implements StatementParser { /* TextractDocumentClient -> BedrockClient */ }
```

### 3.5 Models

```java
class Transaction {
  String transactionId;              // UUID v4
  String userId;                     // Cognito sub
  TransactionType type;              // SPLIT | REIMBURSEMENT
  TransactionStatus status;          // DRAFT | OPEN | EXTERNALLY_ADDED | SETTLED
  String merchant;
  LocalDate transactionDate;         // receipt date or statement date; the dedup key
  Instant createdAt;
  Instant updatedAt;

  BigDecimal subtotal;               // nullable
  BigDecimal tax;                    // nullable
  BigDecimal tip;                    // nullable
  BigDecimal total;

  List<LineItem> items;              // empty for statement-derived and reimbursement txns
  String receiptImageS3Key;          // nullable
  String payerId;                    // personId, or "SELF"
  SplitDefinition splitDefinition;   // nullable until finalized; null for REIMBURSEMENT
  FinalizedSplit finalizedSplit;     // nullable until finalized; null for REIMBURSEMENT

  String sourceStatementImportId;    // nullable; provenance for statement-derived txns
  String dedupKey;                   // normalized, see §4.2
  String notes;                      // nullable
}

enum TransactionType   { SPLIT, REIMBURSEMENT }
enum TransactionStatus { DRAFT, OPEN, EXTERNALLY_ADDED, SETTLED }

class LineItem { String id; String name; BigDecimal price; }

class Person {
  String personId;                   // UUID
  String userId;
  String displayName;
  Instant createdAt;
  Instant lastUsedAt;                // drives most-recent-first ordering in the picker
  boolean archived;                  // soft-delete
}

enum SplitMode { EQUAL, SHARES, PERCENTAGE, EXACT, BY_ITEM }

class SplitDefinition {
  SplitMode mode;
  String payerId;
  List<String> participantIds;
  Map<String, BigDecimal> weights;             // SHARES / PERCENTAGE / EXACT; null otherwise
  Map<String, List<String>> itemAssignments;   // BY_ITEM only: itemId -> participantIds
}

class FinalizedSplit {
  SplitMode mode;
  String payerId;
  Map<String, BigDecimal> participantShares;   // personId -> amount owed
  Instant computedAt;
}

class StatementImport {
  String importId; String userId; String fileName; String contentType;
  String issuerProfile; Instant uploadedAt;
  int rowCount; int candidateCount;
  StatementImportStatus status;      // PARSING | READY | FAILED
  String failureReason;
}

class StatementCandidate {
  String candidateId; String importId;
  LocalDate date; String rawDescription; String normalizedMerchant; BigDecimal amount;
  CandidateClassification classification;
  BigDecimal classificationConfidence;
  List<DuplicateMatch> duplicateMatches;
  CandidateStatus status;            // PENDING | CONFIRMED | DISMISSED
  String resultingTransactionId;     // nullable
}

enum CandidateClassification { LIKELY_SPLIT, LIKELY_REIMBURSEMENT, UNLIKELY }
class DuplicateMatch { String transactionId; String matchStrategy; BigDecimal score; }
```

## 4. Persistence and API contracts

### 4.1 Why a single table

v1 had one entity and no queries, so a bare key-value table sufficed. v2 has three entity types (transaction, person, statement import + candidates) and five access patterns. A single table with a generic `PK`/`SK` plus two GSIs covers all of them without cross-table joins.

Table name: `split-manager-{env}-ledger`. **No TTL attribute.** Point-in-time recovery enabled.

### 4.2 Key design

| Entity | PK | SK |
|---|---|---|
| Transaction | `TXN#{transactionId}` | `META` |
| Person | `USER#{userId}#PEOPLE` | `PERSON#{personId}` |
| Statement import | `IMPORT#{importId}` | `META` |
| Statement candidate | `IMPORT#{importId}` | `CAND#{seq}` |

**GSI1 — chronological ledger listing**

- `GSI1PK` = `USER#{userId}#TXN`
- `GSI1SK` = `{transactionDate}#{transactionId}` (ISO date, so lexical order is chronological)
- Query with `ScanIndexForward=false` for newest-first. Status and type filters apply as a `FilterExpression` — acceptable at personal volume, where the whole partition is on the order of hundreds of items.

**GSI2 — deduplication lookup (BRD FR19)**

- `GSI2PK` = `USER#{userId}#DEDUP#{amountCents}#{transactionDate}`
- `GSI2SK` = `{normalizedMerchant}`

A single query on `GSI2PK` returns every transaction with the same amount and date — the `date + amount` fallback strategy directly. The stricter `merchant + date + amount` match is then applied in-process against `GSI2SK` using normalized comparison (lowercase, strip punctuation, collapse whitespace, strip common payment-processor prefixes such as `SQ *`, `TST*`, `PAYPAL *`) plus a Levenshtein ratio above 0.85. Merchant strings on statements differ enough from receipt vendor names that exact matching alone would miss most true duplicates.

`dedupKey` is stored denormalized on the transaction as `{amountCents}#{transactionDate}#{normalizedMerchant}` for cheap logging and debugging.

### 4.3 Access patterns

| # | Pattern | Mechanism |
|---|---|---|
| 1 | Get transaction by id | `GetItem` on `TXN#{id}` / `META` |
| 2 | List transactions, newest first, paged | `Query` GSI1, `ScanIndexForward=false` |
| 3 | Filter by status and/or type | `Query` GSI1 + `FilterExpression` |
| 4 | Duplicate check | `Query` GSI2 by amount+date, then in-process merchant match |
| 5 | List people | `Query` on `USER#{userId}#PEOPLE` |
| 6 | Get statement import + candidates | `Query` on `IMPORT#{importId}` |
| 7 | Compute balances | Pattern 3 filtered to `OPEN`/`EXTERNALLY_ADDED`, aggregated in-process |

### 4.4 Repositories

```java
class TransactionRepository {
  void save(Transaction t);
  Optional<Transaction> findById(String id);
  Page<Transaction> listByDateDesc(String userId, TransactionStatus s, TransactionType t, String cursor, int limit);
  List<Transaction> findByAmountAndDate(String userId, BigDecimal amount, LocalDate date);   // GSI2
  void delete(String id);
}

class PersonRepository {
  void save(Person p);
  List<Person> findAll(String userId);
  Optional<Person> findById(String userId, String personId);
  Optional<Person> findByDisplayName(String userId, String displayName);
  void delete(String userId, String personId);
}

class StatementImportRepository {
  void saveImport(StatementImport i);
  void saveCandidates(String importId, List<StatementCandidate> c);   // BatchWriteItem
  Optional<StatementImport> findImport(String importId);
  List<StatementCandidate> findCandidates(String importId);
  void updateCandidate(StatementCandidate c);
}
```

All via the DynamoDB Enhanced Client, with models annotated `@DynamoDbBean`. `BigDecimal` money values persist as `N`; `LocalDate` as ISO-8601 `S`; `Instant` as epoch-second `N`.

### 4.5 Representative contracts

**`POST /transactions/from-receipt`** — `multipart/form-data`, field `image`. Response `201`:

```json
{
  "transactionId": "uuid",
  "type": "SPLIT",
  "status": "DRAFT",
  "merchant": "string | null",
  "transactionDate": "2026-08-24",
  "items": [{ "id": "string", "name": "string", "price": 0 }],
  "subtotal": 0, "tax": 0, "tip": null, "total": 0,
  "duplicateWarnings": [
    { "transactionId": "uuid", "merchant": "string", "transactionDate": "2026-08-01",
      "total": 0, "matchStrategy": "MERCHANT_DATE_AMOUNT", "score": 0.95 }
  ]
}
```

**`POST /transactions/{id}/finalize`** — body:

```json
{
  "split": {
    "mode": "EQUAL | SHARES | PERCENTAGE | EXACT | BY_ITEM",
    "payerId": "SELF",
    "participantIds": ["personId", "..."],
    "weights": { "personId": 2 },
    "itemAssignments": { "itemId": ["personId"] }
  },
  "tip": 0,
  "newPersonNames": ["Alex"]
}
```

Response `200` returns the full transaction plus:

```json
{
  "summary": {
    "amountOwedByPerson": { "personId": 0 },
    "shareText": "Dinner at Merchant — $120.00 total\nAlex owes you $40.00\n..."
  }
}
```

Idempotent on an already-finalized transaction: regenerates the summary from the stored split rather than erroring. The server always **recomputes** the split from the definition and never trusts client-supplied share amounts (except in `EXACT` mode, where the amounts *are* the definition and are validated to sum to the total).

**`PATCH /transactions/{id}/status`** — `{ "status": "EXTERNALLY_ADDED" }`. Valid transitions: `DRAFT→OPEN` (via finalize only), `OPEN→EXTERNALLY_ADDED`, `OPEN→SETTLED`, `EXTERNALLY_ADDED→SETTLED`, and `SETTLED→OPEN` / `EXTERNALLY_ADDED→OPEN` as corrections. Anything else is `409`.

**`GET /balances`** — response:

```json
{
  "balances": [ { "personId": "uuid", "displayName": "Alex", "netAmount": 62.50,
                  "openTransactionCount": 3 } ],
  "totalOwedToUser": 148.75
}
```

`netAmount` positive means they owe the user.

**`POST /statements`** — `multipart/form-data`, fields `file` and optional `issuerProfile`. Response `201` returns the import plus its classified, dedup-checked candidates.

## 5. Split-calculation algorithm

All five modes reduce to one pipeline: **resolve weights → distribute proportionally → assign the rounding remainder to the payer.** This replaces v1's two separate methods; `computeEqualSplit` and `computeItemSplit` become weight-resolution cases.

### 5.1 Weight resolution

```
resolveWeights(definition, items):
  switch definition.mode:
    EQUAL:      return { p: 1 for p in definition.participantIds }
    SHARES:     return definition.weights                  // whole numbers, e.g. {A:2, B:1}
    PERCENTAGE: validate sum(weights) == 100
                return definition.weights
    EXACT:      validate sum(weights) == total             // amounts are the weights
                return definition.weights
    BY_ITEM:    subtotals = {}
                for item in items:
                    sharers = definition.itemAssignments[item.id]
                    perPerson = item.price / sharers.size()      // unrounded BigDecimal
                    for p in sharers: subtotals[p] += perPerson
                return subtotals                            // tax and tip follow subtotal share
```

### 5.2 Distribution

```
distribute(total, weights):
  totalWeight = sum(weights.values())
  shares = {}
  for p, w in weights:
      shares[p] = roundHalfUp(total * (w / totalWeight), 2)
  return shares
```

### 5.3 Rounding remainder

```
applyRoundingRemainder(shares, total, payerId):
  remainder = total - sum(shares.values())     // typically within a few cents of zero
  if payerId is a participant:
      shares[payerId] += remainder
  else:
      distribute one cent at a time, in participantIds order, until remainder is zero
```

**Invariant, asserted in unit tests for every mode:** `sum(shares.values()) == total` exactly. `SplitSummaryService` depends on this.

`EXACT` mode short-circuits distribution — the weights *are* the shares — but still runs the remainder step, which is a no-op given the sum validation.

For a `REIMBURSEMENT`, `SplitCalculationService` is not called at all: the full amount belongs to the user and is claimed from the employer.

## 6. Statement parsing

### 6.1 CSV — deterministic, ships first

`CsvStatementParser` maps columns via a per-issuer profile:

```java
record IssuerProfile(String id, String dateColumn, String descriptionColumn,
                     String amountColumn, String debitCreditColumn,
                     String dateFormat, boolean debitsArePositive) {}
```

Profiles live in `src/main/resources/issuer-profiles.yml`. If `issuerProfile` is absent, the parser infers columns from the header row by name matching and falls back to positional heuristics. Credits (payments, refunds) are discarded — only debits can be split.

### 6.2 PDF — Textract plus Bedrock

```
PdfStatementParser.parse(bytes, issuerProfile):
  tables = textractDocumentClient.analyzeTables(bytes)     // Textract TABLES feature
  rows   = bedrockClient.normalizeStatementRows(tables)    // Converse, JSON-schema output, temp 0
  validate each row: date parses, amount parses, description non-empty
  drop credits; drop rows failing validation, recording the count
  return rows
```

The Bedrock prompt receives the extracted table cells (never the raw PDF) and returns a strict JSON array of `{date, description, amount, isCredit}`. Constrained decoding against the schema, temperature 0, and post-parse validation together bound the non-determinism. Rows that fail validation are reported in `StatementImport.failureReason` as a count rather than silently dropped — the user needs to know the parse was partial.

**This is the highest-risk component in the system** (HLD "Key architectural risks"). It should be prototyped against a real statement before the review UI is committed to.

### 6.3 Classification

`StatementClassificationService.classify(row)` runs in order, first match wins:

1. **User history** — the normalized merchant appears on a previously-confirmed transaction. Returns that transaction's type with confidence 0.95. This is the strongest signal and it improves with use.
2. **Reimbursement keywords** — `UBER`, `LYFT`, `TRANSIT`, `METRO`, `MTA`, `AMTRAK`, and similar. Returns `LIKELY_REIMBURSEMENT`, confidence 0.8.
3. **Split heuristics** — restaurant/bar/grocery merchant patterns, or any amount above a configurable threshold (default 40.00). Returns `LIKELY_SPLIT`, confidence 0.6.
4. Otherwise `UNLIKELY`, confidence 0.0.

Candidates are surfaced newest-first with `LIKELY_*` shown by default and `UNLIKELY` collapsed behind a "show all" toggle. Deliberately conservative: precision matters more than recall, because a noisy candidate list stops being reviewed.

## 7. Deduplication

```
findMatches(merchant, date, amount):
  candidates = []
  for d in [date-3d .. date+3d]:                          // settlement lag between purchase and posting
      candidates += transactionRepository.findByAmountAndDate(userId, amount, d)   // GSI2

  matches = []
  for c in candidates:
      score = levenshteinRatio(normalize(merchant), normalize(c.merchant))
      if score >= 0.85: matches.add(MERCHANT_DATE_AMOUNT, score)
      else:             matches.add(DATE_AMOUNT, 0.5)      // weaker; still worth showing
  return matches sorted by score desc
```

The ±3-day window exists because a card statement posts a charge days after the receipt date. `normalize()` lowercases, strips punctuation, collapses whitespace, and removes processor prefixes (`SQ *`, `TST*`, `PAYPAL *`, `SP `).

**Matches are surfaced as warnings, never auto-merged.** A genuinely repeated charge — the same coffee shop, same amount, next day — is a real transaction, and silently hiding it would be a worse failure than showing a dismissible warning. The user decides.

## 8. `frontend/` — React + TypeScript

### 8.1 Routing

```
/                       -> LedgerPage           (transaction list + balances)
/login                  -> LoginRedirect        (Cognito hosted UI)
/auth/callback          -> AuthCallbackPage     (OAuth code exchange)
/transactions/:id       -> TransactionDetailPage
/capture                -> ReceiptCapturePage
/split/:id              -> SplitEditorPage
/statements             -> StatementImportPage
/statements/:id/review  -> CandidateReviewPage
/reimbursements         -> ReimbursementsPage   (filtered ledger + export)
/people                 -> PeoplePage
```

All routes except `/login` and `/auth/callback` sit behind a `<RequireAuth>` wrapper that redirects to the hosted UI when no valid token is held.

### 8.2 Component tree

```
App
 ├─ AuthProvider                     (token state, silent refresh, logout)
 ├─ AppShell                         (nav: Ledger | Capture | Import | Reimbursements | People)
 ├─ LedgerPage
 │   ├─ BalanceSummaryBar            (per-person net; "who owes me what")
 │   ├─ TransactionFilters           (status, type)
 │   └─ TransactionList -> TransactionRow (repeated)
 ├─ TransactionDetailPage
 │   ├─ TransactionHeader            (merchant, date, total, type)
 │   ├─ SplitBreakdown               (per-person amounts, read-only)
 │   ├─ ShareTextPanel               (summary + copy action)
 │   └─ StatusActionBar              (Mark as externally added | Mark settled | Reopen)
 ├─ ReceiptCapturePage -> FileInput (capture="environment") + DuplicateWarningBanner
 ├─ SplitEditorPage
 │   ├─ ReceiptReviewSection         (ReceiptItemRow*, AddItemButton)      [CARRIED OVER]
 │   ├─ TipEntrySection              (18/20/25% presets + manual)          [CARRIED OVER]
 │   ├─ PayerSelector                                                       [NEW]
 │   ├─ ParticipantsSection -> PersonPicker (saved directory + inline add) [REWORKED]
 │   ├─ SplitModeToggle              (Equal|Shares|Percentage|Exact|ByItem)[EXTENDED]
 │   ├─ WeightEntryGrid              (Shares/Percentage/Exact)              [NEW]
 │   ├─ ItemAssignmentGrid           (ByItem only)                         [CARRIED OVER]
 │   ├─ SplitSummary                 (live client-side preview)            [CARRIED OVER]
 │   └─ ConfirmButton + ConfirmationModal
 ├─ StatementImportPage -> FileInput (.csv,.pdf) + IssuerProfileSelector
 ├─ CandidateReviewPage
 │   └─ CandidateRow*                (classification badge, duplicate warning,
 │                                    inline edit, Confirm | Dismiss)
 ├─ ReimbursementsPage -> ReimbursementList + ExportSummaryButton
 └─ PeoplePage -> PersonRow* (rename, archive)
```

### 8.3 Key props

```ts
interface PersonPickerProps {
  people: Person[];                     // saved directory, most-recently-used first
  selectedIds: string[];
  onSelectionChange: (ids: string[]) => void;
  onCreatePerson: (displayName: string) => Promise<Person>;   // BRD FR13 inline add
}

interface WeightEntryGridProps {
  mode: 'SHARES' | 'PERCENTAGE' | 'EXACT';
  people: Person[];
  weights: Record<string, number>;
  total: number;                        // for EXACT/PERCENTAGE validation feedback
  onWeightsChange: (w: Record<string, number>) => void;
}

interface CandidateRowProps {
  candidate: StatementCandidate;
  onConfirm: (edited: CandidateEdit) => void;
  onDismiss: () => void;
}

interface StatusActionBarProps {
  status: TransactionStatus;
  onStatusChange: (next: TransactionStatus) => void;   // allowed transitions only
}
```

### 8.4 State management

v1's "local component state only" no longer holds — auth tokens, the people directory, and the transaction list are cross-page state. **TanStack Query** handles server state (caching, invalidation, optimistic status updates); a small React context holds auth. No Redux.

The split editor keeps its working draft in local `useReducer` state and only writes on finalize, preserving v1's "nothing persists mid-edit" property.

`splitCalculation.ts` mirrors §5 for live preview only. The server recomputes and remains the source of truth.

### 8.5 API client

```ts
// Every call attaches Authorization: Bearer <token>; a 401 triggers refresh, then re-login.
transactions: { createFromReceipt, create, list, get, update, finalize, updateStatus, delete }
statements:   { upload, getCandidates, confirmCandidate, dismissCandidate }
people:       { list, create, rename, delete }
balances:     { get }
```

## 9. Authentication flow

1. Unauthenticated visit → `<RequireAuth>` redirects to the Cognito hosted UI (authorization code + PKCE).
2. Cognito redirects to `/auth/callback?code=...`; the SPA exchanges the code for ID/access/refresh tokens.
3. Access token is held **in memory**; the refresh token goes to `sessionStorage`. Neither is written to `localStorage` — this is financial data and XSS persistence is the concern.
4. Every API call sends `Authorization: Bearer <accessToken>`.
5. The API validates signature (JWKS), issuer, audience, and expiry; `userId` comes from the `sub` claim and is never accepted from the request body.
6. On 401, the client silently refreshes once; a second failure forces re-login.

## 10. Error handling

`GlobalExceptionHandler` maps to RFC 7807 `application/problem+json`:

| Exception | Status |
|---|---|
| `TransactionNotFoundException`, `PersonNotFoundException` | 404 |
| `IllegalStatusTransitionException` | 409 |
| `ValidationException` (weights don't sum, empty participants) | 400 |
| `StatementParseException` | 422 |
| `TextractException`, `BedrockException` | 502 |
| Spring Security JWT failure | 401 |

Statement parse failures are **partial-success by design**: a batch that extracts 40 of 45 rows returns `READY` with 40 candidates and a `failureReason` naming the 5 dropped rows, rather than failing the whole import.

## 11. `integ-tests/`

**Package:** `com.splitmanager.integtests`

Every test now authenticates first: a dedicated Beta-only Cognito test user, credentials in Secrets Manager, `AuthHelper` performs `InitiateAuth` (USER_SRP) once per run and caches the token.

```java
class AuthIntegTest        { @Test void unauthenticatedRequestIsRejected(); }        // 401 — the FR25 guard
class TransactionIntegTest { @Test void createFromReceiptThenFinalizeThenList(); }
class SplitModeIntegTest   { @Test void allFiveModesSumExactlyToTotal(); }
class StatusIntegTest      { @Test void markExternallyAddedThenSettled();
                             @Test void illegalTransitionRejected(); }
class PeopleIntegTest      { @Test void personSavedOnFinalizeAndReusable(); }        // FR13
class StatementIntegTest   { @Test void csvImportProducesClassifiedCandidates(); }   // bundled sample CSV
class DedupIntegTest       { @Test void duplicateChargeIsFlaggedNotAutoAdded(); }    // FR19
class BalanceIntegTest     { @Test void balancesReflectOpenTransactionsOnly(); }
```

`AuthIntegTest` is the most important test in the suite — it is the automated proof that FR25 holds, and a regression there is the one failure mode that would expose everything.

PDF statement parsing is **not** integ-tested against Bedrock in the pipeline: it is non-deterministic and costs a model call per run. It is covered by unit tests against recorded Textract output with a stubbed `BedrockClient`, and verified manually.

## 12. Migration from the v1 build

### Carried over unchanged

- All of `infra/`: `PipelineStack`, `AppStage`, `FrontendStack`, the CodePipeline wiring and integ-test gate.
- `TextractExpenseClient` (renamed from `TextractClient`) and `ReceiptParsingService`.
- `SplitSummaryService` — output now stored on the transaction rather than returned once.
- Frontend `ReceiptReviewSection`, `ReceiptItemRow`, `AddItemButton`, `TipEntrySection`, `ItemAssignmentGrid`, `ItemAssignmentRow`, `SplitSummary`, `ConfirmationModal`.
- `SplitCalculationServiceTest` and `SplitSummaryServiceTest` — the rounding-remainder cases remain valid and become the regression net for the §5 refactor.

### Modified

- `SplitCalculationService` — `computeEqualSplit`/`computeItemSplit` collapse into the §5 pipeline. **The rounding-remainder logic, which is the genuinely hard part, is preserved as-is.**
- `DataStack` — TTL removed, PITR enabled, GSIs added, statements bucket added.
- `ApiStack` — Cognito env vars, Bedrock and `AnalyzeDocument` IAM grants, `s3:DeleteObject`.
- `WebConfig` CORS — must now resolve to the real CloudFront origin. The `FRONTEND_ORIGIN` default of `*` is incompatible with credentialed requests and is a hard blocker for auth.
- `ParticipantsSection` — free-text entry becomes `PersonPicker` over the saved directory.
- `apiClient.ts` — all calls gain the bearer token; endpoints renamed.

### Deleted

- `ReceiptSession`, `SessionStatus`, `ReceiptSessionRepository`, `ReceiptSessionService` — replaced by `Transaction` and friends.
- The `expiresAt` TTL attribute and every assumption of session expiry.
- `SessionController` and `GET /session/{sessionId}`.
- `ExpenseController` and the v1 `POST /finalize-split` contract.
- `SessionNotFoundException`.
- Frontend `SplitPage` as a single-route app, `useReceiptSession`, and the v1 `UploadPage` (its file-input logic moves into `ReceiptCapturePage`).
- v1 `ParseReceiptIntegTest`, `SessionIntegTest`, `FinalizeSplitIntegTest` — superseded by §11.

### Sequencing constraint

**Auth must land before the persistent ledger is exposed on a deployed environment.** Deploying a permanent transaction store behind the current unauthenticated ALB, even to Beta, publishes financial data to the internet. Auth and CORS are therefore the first work item, not a later hardening pass.

## 13. `lambdas/`

Still reserved, still empty. Statement ingestion is the one plausible future occupant: PDF parsing through Textract plus Bedrock can take tens of seconds, which is tolerable as a synchronous call at personal volume but would move to an async job if it ever exceeded the ALB idle timeout. Not built now.

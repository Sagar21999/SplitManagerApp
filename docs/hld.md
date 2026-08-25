# Split Manager — High-Level Design (HLD)

*Companion documents: `brd.md` (business "why") and `lld.md` (implementation detail — exact classes, methods, schemas, algorithms). This document is the architectural bridge between the two: components, data flow, technology choices, and security/scalability posture, without engineering-level detail.*

*Revision note: this is v2, matching BRD v2. The v1 architecture — transient sessions, no auth, an iOS Shortcut entry point — is preserved in git history at commit 6e814bb. The section "Architectural changes from v1" records what moved and why.*

## Purpose of this document

Translate the BRD's requirements into a system architecture: what components exist, how they interact, what data moves where, what technology each piece uses, and what the security and scalability posture is. It does not specify class names, method signatures, database attribute types, or algorithms — see the LLD for that.

## System overview

```
                        ┌──────────────────────────────┐
                        │   Amazon Cognito User Pool    │
                        │   (single user, hosted UI)    │
                        └───────────┬──────────────────┘
                                    │ OIDC login, returns JWT
                                    ▼
 ┌────────────────────────────────────────────┐
 │  Frontend SPA (React, S3 + CloudFront)      │
 │  ┌──────────┬───────────┬────────────────┐  │
 │  │ Ledger   │ Receipt   │ Statement      │  │
 │  │ + People │ capture   │ import review  │  │
 │  └──────────┴───────────┴────────────────┘  │
 └───────────────────┬────────────────────────┘
                     │ HTTPS + Authorization: Bearer <JWT>
                     ▼
 ┌────────────────────────────────────────────┐
 │  API (Spring Boot, Java) — ECS Fargate + ALB │
 │  validates JWT against Cognito JWKS          │
 │  ┌────────────┬──────────────┬────────────┐  │
 │  │ Receipt    │ Statement    │ Ledger /   │  │
 │  │ parsing    │ ingestion    │ split calc │  │
 │  └─────┬──────┴──────┬───────┴─────┬──────┘  │
 └────────┼─────────────┼─────────────┼─────────┘
          ▼             ▼             ▼
   ┌────────────┐ ┌────────────┐ ┌──────────────┐
   │ Textract   │ │  Bedrock   │ │  DynamoDB     │
   │ Expense +  │ │ (statement │ │  (permanent   │
   │ Document   │ │ normalize) │ │   ledger)     │
   └────────────┘ └────────────┘ └──────────────┘
          │                             
          ▼                             
   ┌──────────────────────────────┐
   │ S3 — receipt images (retained)│
   │      statement files (purged  │
   │      after extraction)        │
   └──────────────────────────────┘
```

Supporting, not in the request path: a CDK Pipelines-driven CodePipeline (`infra/`) builds and deploys all of the above to Beta and then Prod.

## Components and responsibilities

- **Cognito User Pool** — the single user's identity. Hosted UI handles login; the SPA receives a JWT and attaches it to every API call. Exactly one user exists; self-registration is disabled.
- **Frontend (React SPA, S3 + CloudFront)** — three surfaces sharing one shell: the **ledger** (transaction list, transaction detail, per-person balances, people directory), **receipt capture** (upload, review, split, finalize), and **statement import review** (upload, triage suggested candidates, confirm or dismiss).
- **API (Java/Spring Boot, ECS Fargate)** — the only component with AWS credentials. Validates the JWT on every request, then orchestrates Textract, Bedrock, DynamoDB, and S3 access along with split computation, deduplication, and summary generation.
- **AWS Textract** — two distinct uses: `AnalyzeExpense` for receipt OCR and structured field/line-item extraction, and `AnalyzeDocument` (table extraction) as the first stage of PDF statement parsing.
- **Amazon Bedrock** — second stage of PDF statement parsing: normalizes Textract's raw table output into consistent transaction rows across issuer layouts, and assists the split-candidate classifier. Not used for receipts, where `AnalyzeExpense` is already purpose-built.
- **DynamoDB** — the permanent ledger: transactions, the people directory, and statement import batches. No TTL on transaction data.
- **S3** — receipt images (retained, referenced by key from the transaction record) and uploaded statement files (deleted after extraction, per BRD FR20).
- **CDK Pipeline (`infra/`)** — builds and deploys every other component.

## Data flow

### Receipt path

1. Authenticated SPA → `POST /transactions/from-receipt` (image + JWT) → API.
2. API stores the image in S3, calls Textract `AnalyzeExpense`, and creates a **draft transaction** in DynamoDB holding the parsed merchant, items, tax, and total.
3. API runs the duplicate check against existing transactions (merchant + date + amount, falling back to date + amount) and returns any matches alongside the draft.
4. SPA loads the draft, and the user edits items, tip, payer, participants, and split mode entirely client-side — nothing persists mid-edit.
5. SPA → `POST /transactions/{id}/finalize` → API recomputes the split server-side, persists the finalized transaction as **open**, and returns the per-person breakdown plus shareable summary text.
6. Any newly-used participant name is written to the people directory as a side effect.

### Statement path

1. Authenticated SPA → `POST /statements` (CSV or PDF) → API stores the file in S3.
2. **CSV**: parsed deterministically with a per-issuer column mapping. **PDF**: Textract `AnalyzeDocument` extracts tables, then Bedrock normalizes rows into a consistent transaction shape.
3. API classifies each extracted row as a likely split candidate, a likely reimbursement, or neither — using merchant-category heuristics, an amount threshold, and the user's own history of previously-split merchants.
4. API runs each candidate through the duplicate check and flags matches.
5. API deletes the raw statement file from S3, retaining only extracted rows.
6. SPA presents candidates for triage. The user edits, confirms (entering the normal split flow from step 4 of the receipt path), or dismisses each one.

### Ledger path

- `GET /transactions` — chronological list, filterable by status and type.
- `GET /transactions/{id}` — full detail plus regenerated summary.
- `PATCH /transactions/{id}/status` — the status transitions of BRD FR14, including "mark as externally added." Local state change only; no outbound call.
- `GET /balances` — per-person net across all open transactions.
- `GET|POST|PATCH|DELETE /people` — the saved people directory.

## Technology stack

| Layer | Choice | Why |
|---|---|---|
| Infrastructure as code | AWS CDK (TypeScript) | Type-safe infra, self-mutating pipeline support via CDK Pipelines |
| CI/CD | AWS CodePipeline, via CDK Pipelines | Single AWS-native pipeline; Beta/Prod stages defined in code |
| Authentication | Amazon Cognito User Pool + hosted UI | Managed OIDC for one user; no password handling in our code; JWTs validate statelessly at the API |
| Frontend | React + TypeScript (client-side SPA) | No server-rendering need; the ledger and split UIs are entirely client-driven after fetch |
| Frontend hosting | S3 + CloudFront | Cheap static hosting, HTTPS termination, low operational overhead |
| Backend | Java, Spring Boot, Maven | Standard, well-supported REST framework fitting the user's specified stack |
| Backend hosting | ECS Fargate + Application Load Balancer | Containerized, no server management, fits a CodePipeline-driven deploy model |
| Database | DynamoDB (single table) | Serverless, on-demand billing; personal-scale access patterns are all key lookups or small queries |
| Object storage | S3 | DynamoDB's 400KB item limit is smaller than a phone photo or a statement PDF |
| Receipt parsing | AWS Textract `AnalyzeExpense` | Purpose-built for receipts; already proven in v1 |
| PDF statement parsing | Textract `AnalyzeDocument` + Amazon Bedrock | Table extraction is mechanical; normalizing wildly varying issuer layouts is not, and is exactly what an LLM handles well |
| CSV statement parsing | Plain Java, per-issuer column mapping | Deterministic, testable, no model call needed |

## API surface

High-level only — full request/response contracts are in the LLD. Every endpoint requires a valid JWT.

**Transactions**
- `POST /transactions/from-receipt` — upload a photo, get a parsed draft transaction.
- `POST /transactions` — create a transaction manually (no receipt).
- `GET /transactions` — list, filtered by status/type.
- `GET /transactions/{id}` — detail plus summary.
- `PUT /transactions/{id}` — edit a draft.
- `POST /transactions/{id}/finalize` — compute and persist the split; return the breakdown and shareable summary.
- `PATCH /transactions/{id}/status` — status transitions, including "mark as externally added."
- `DELETE /transactions/{id}`

**Statements**
- `POST /statements` — upload CSV/PDF; returns an import batch with classified, dedup-checked candidates.
- `GET /statements/{id}/candidates` — retrieve the batch for review.
- `POST /statements/{id}/candidates/{candidateId}/confirm` — promote a candidate into a draft transaction.
- `POST /statements/{id}/candidates/{candidateId}/dismiss`

**People and balances**
- `GET|POST /people`, `PATCH|DELETE /people/{id}` — the saved directory.
- `GET /balances` — per-person net across open transactions.

**Reimbursements** are `GET /transactions?type=REIMBURSEMENT` plus a summary export — not a separate resource.

## Security

- **Authentication is mandatory on every endpoint.** The API validates the Cognito-issued JWT's signature against the user pool's JWKS, plus issuer, audience, and expiry. There is no unauthenticated route other than the health check.
- Exactly one user exists. Self-registration is disabled on the user pool; the account is provisioned manually.
- No public S3 access. Receipt images and statement files are only ever read by the API using its own IAM role, never served directly to a client.
- **Raw statement files are deleted after extraction**, so the durable blast radius of an S3 compromise is receipt images plus extracted rows, not original bank documents.
- AWS-service access (DynamoDB, S3, Textract, Bedrock) is via the ECS task's scoped IAM role, not static access keys.
- HTTPS everywhere: CloudFront terminates TLS for the frontend, the ALB terminates TLS for the API.
- CORS on the API is restricted to the frontend's CloudFront domain.
- DynamoDB and S3 are encrypted at rest; the ledger is permanent financial data, so point-in-time recovery is enabled on the table.

**On the v1 posture:** v1 deliberately shipped without auth, reasoning that transient, self-deleting session data made the exposure trivial. That reasoning does not survive persistence. A permanent ledger plus imported bank transactions behind a public ALB would expose the user's complete financial history to anyone who found the URL. Authentication is therefore a prerequisite for the persistent ledger, and must land before statement import ships.

## Scalability & reliability

- Personal scale: on the order of hundreds of transactions per year and a handful of statement imports per month. On-demand DynamoDB billing absorbs this with no capacity planning.
- The API is stateless — all state lives in DynamoDB, so any running task can serve any request.
- **Balances are computed on read** by aggregating open transactions rather than maintained as running counters. This avoids a class of consistency bugs, and at personal volumes the query cost is negligible. If the ledger ever grew large enough for this to matter, a materialized per-person balance record updated on status change is the migration path.
- Receipt images are retained; statement files are purged after extraction via an S3 lifecycle rule as a backstop to the API's explicit delete.
- Beta and Prod are fully separate resource sets. **Beta is the working environment**: all verification happens there, against real receipts and real statements. Prod exists and receives promotions, but is not the focus — a broken Prod is tolerable, a broken Beta is not.
- **Nothing in the system depends on an external third party at request time.** Splitwise is a manual, downstream, non-blocking destination, so no outage or API change there can block a transaction.

## Architectural changes from v1

| Area | v1 | v2 | Why |
|---|---|---|---|
| Identity | None | Cognito user pool, JWT on every call | Persistent financial data cannot sit behind an open endpoint |
| Persistence | Sessions with a DynamoDB TTL | Permanent transactions, no TTL | The ledger, balances, statuses, and dedup all require durable records |
| Data model | Single-entity table, key lookups only | Single-table with two GSIs | New access patterns: chronological listing, status filtering, dedup lookup |
| Entry point | iOS Shortcut → web view | Authenticated web app, two input paths | The Shortcut was dropped in v1's build already; statements are the new second path |
| Frontend | One route (`/split/:sessionId`) | Multi-page app (ledger, detail, capture, import, people) | A ledger needs navigation |
| Split modes | Equal, by item | Equal, shares, percentage, exact, by item | All five are one weight-resolution algorithm; see LLD §5 |
| AI services | Textract only | Textract + Bedrock | PDF statement layouts vary too much for deterministic parsing |
| Splitwise | Manual paste, terminal step | Manual paste, non-blocking status flag | Split Manager is now the record; Splitwise is optional downstream |

## Key architectural risks

- **PDF statement parsing is the highest-uncertainty component in the system.** Issuer layouts vary and change without notice, and the Textract-plus-Bedrock pipeline is non-deterministic in a way nothing else here is. It should be prototyped against a real statement before the surrounding UI is committed to. CSV import is deterministic and should ship first so the review flow is proven independently of the hard parser.
- **Deduplication correctness is what makes the two-input-path design safe.** Both false negatives (silent doubles in the ledger) and false positives (a genuinely repeated charge at the same merchant hidden as a duplicate) are user-visible errors. The design mitigates the second by flagging for review rather than auto-merging.
- **Classification quality determines whether statement import is useful or noise.** Too many false candidates and the user stops reviewing them. Starting with conservative heuristics and learning from the user's own accept/dismiss decisions is preferable to an aggressive model.

## Environments & delivery

One AWS account, Beta and Prod distinguished by resource naming rather than account isolation. One monorepo, one CodePipeline: parallel per-package builds, automatic Beta deploy, then automatic Prod promotion with no manual approval step. Each environment has its own Cognito user pool.

**There is no automated integration-test gate.** The suite was removed to keep iteration fast on a solo personal project; unit tests plus manual verification against Beta are the quality bar. This means Prod promotion is ungated — an accepted risk, since Beta is the environment that actually matters and Prod carries no other users. Reintroducing a gate is a known, deliberate follow-up if Prod ever becomes load-bearing.

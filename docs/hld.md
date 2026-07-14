# Split Manager — High-Level Design (HLD)

*Companion documents: `brd.md` (business "why") and `lld.md` (implementation detail — exact classes, methods, schemas, algorithms). This document is the architectural bridge between the two: components, data flow, technology choices, and security/scalability posture, without engineering-level detail.*

## Purpose of this document

Translate the BRD's requirements into a system architecture: what components exist, how they interact, what data moves where, what technology each piece uses, and what the security and scalability posture is. It does not specify class names, method signatures, database attribute types, or algorithms — see the LLD for that.

## System overview

```
 ┌───────────────┐        HTTPS         ┌─────────────────────────────┐
 │  iOS Shortcut  │ ───────────────────▶│   API (Spring Boot, Java)     │
 │  (capture /    │◀─────────────────── │   ECS Fargate + ALB           │
 │  share sheet)  │   parsed fields +    └──────────┬──────────┬────────┘
 └───────┬────────┘   split-page URL                │          │
         │ opens URL in                             ▼          ▼
         │ in-Shortcut web view              ┌────────────┐ ┌─────────────┐
         ▼                                   │ AWS Textract│ │  DynamoDB    │
 ┌────────────────┐                          │(AnalyzeExpense)│(ReceiptSession)│
 │  Frontend SPA   │                         └────────────┘ └─────────────┘
 │  (React, S3 +   │  HTTPS calls                            ┌─────────────┐
 │  CloudFront)    │ ───────────────────▶ API (above) ──────▶│  S3 (receipt │
 └─────────────────┘                                          │  images)     │
                                                                └─────────────┘
                                            API (above) ───────▶ ┌─────────────┐
                                                                  │ Splitwise API│
                                                                  │ (external)   │
                                                                  └─────────────┘
```

Supporting, not in the request path: a CDK Pipelines-driven CodePipeline (`infra/`) builds and deploys all of the above; an `integ-tests/` suite gates promotion from Beta to Prod.

## Components and responsibilities

- **iOS Shortcut** — the capture trigger. Converts HEIC to JPEG, POSTs the photo to the API, and opens the API's returned URL in an in-Shortcut web view.
- **Frontend (React SPA, S3 + CloudFront)** — the split-assignment UI: loads the parsed receipt, lets the user review/correct it, enter a tip, choose participants, choose a split mode, and confirm.
- **API (Java/Spring Boot, ECS Fargate)** — the only component with AWS/Splitwise credentials. Orchestrates the Textract call, the Splitwise calls, DynamoDB/S3 access, and the split computation itself.
- **AWS Textract** — receipt OCR and structured field/line-item extraction.
- **DynamoDB** — short-lived session state (the in-progress receipt: parsed fields, corrections, eventual split, outcome).
- **S3** — receipt image storage, referenced by key from the DynamoDB session record.
- **Splitwise API (external)** — the actual system of record for the resulting expense; everything upstream exists to build one correct request to this API.
- **CDK Pipeline (`infra/`)** — builds and deploys every other component.
- **`integ-tests/`** — runs against live Beta after each deploy; a pass is what promotes a build to Prod.

## Data flow

1. Shortcut → `POST /parse-receipt` (image) → API.
2. API stores the image in S3, calls Textract to extract fields, writes a new session record to DynamoDB (status: parsed), and returns the parsed fields plus a split-page URL to the Shortcut.
3. Shortcut opens that URL in its web view; the frontend loads and fetches the session's current state from the API.
4. Frontend → `GET /friends` → API → Splitwise (`get_friends`, `get_groups`) → back to the frontend, to populate the "who was there" step.
5. The user edits items, tip, participants, and split mode entirely client-side — nothing is persisted mid-edit.
6. Frontend → `POST /submit-expense` (session ID + finalized split) → API.
7. API reads the session from DynamoDB, reads the image from S3, calls Splitwise's `create_expense` with the image attached and the finalized per-person amounts, and updates the session's status.
8. API → Frontend: success/failure and the resulting Splitwise expense ID.
9. Frontend shows a plain confirmation; the Shortcut can return to a clean state.

## Technology stack

| Layer | Choice | Why |
|---|---|---|
| Infrastructure as code | AWS CDK (TypeScript) | Type-safe infra, self-mutating pipeline support via CDK Pipelines |
| CI/CD | AWS CodePipeline, via CDK Pipelines | Single AWS-native pipeline; Beta/Prod stages defined in code |
| Frontend | React + TypeScript (client-side SPA) | No server-rendering need — the split page is entirely client-driven after an initial fetch |
| Frontend hosting | S3 + CloudFront | Cheap static hosting, HTTPS termination, low operational overhead |
| Backend | Java, Spring Boot, Maven | Standard, well-supported REST framework fitting the user's specified stack |
| Backend hosting | ECS Fargate + Application Load Balancer | Containerized, no server management, fits a CodePipeline-driven, environment-scoped deploy model |
| Database | DynamoDB | Serverless, on-demand billing, native TTL fits inherently short-lived session data |
| Object storage | S3 | DynamoDB's 400KB item limit is smaller than a typical phone photo |
| Receipt parsing | AWS Textract (`AnalyzeExpense`) | Purpose-built for receipts/invoices; keeps the stack fully AWS-native with no separate vendor account |
| Third-party integration | Splitwise REST API v3.0 | The product of record for the actual expense/split |

## API surface

High-level only — full request/response contracts are in the LLD.

- `POST /parse-receipt` — submit a photo, get back parsed fields and a split-page link.
- `GET /session/{sessionId}` — the frontend's initial load, to fetch the current session state.
- `GET /friends` — Splitwise friends and groups, for the "who was there" step.
- `POST /submit-expense` — finalize and post the expense to Splitwise.

## Security

- No public S3 access. Receipt images are only ever read by the API, using its own IAM role — never served directly to a client.
- Splitwise's API key lives in AWS Secrets Manager and is injected into the ECS task at runtime — never committed, never a plain environment variable, never returned to the frontend.
- AWS-service access (DynamoDB, S3, Textract) is via the ECS task's scoped IAM role, not static access keys.
- HTTPS everywhere: CloudFront terminates TLS for the frontend, the ALB terminates TLS for the API.
- CORS on the API is restricted to the frontend's CloudFront domain.
- No authentication/authorization layer exists for the first release — this is a deliberate, explicit gap acceptable for a single-user personal tool, and would need to be revisited before any multi-user future.

## Scalability & reliability

- DynamoDB's on-demand billing absorbs personal-scale traffic with no capacity planning.
- The API is stateless — all session state lives in DynamoDB, so any running task can serve any request; ECS could scale task count if load ever justified it, though P0 doesn't need it.
- DynamoDB's TTL attribute auto-expires session records; an S3 lifecycle rule independently backstops image cleanup.
- Beta and Prod are fully separate resource sets (not a shared environment with a feature flag), and promotion from Beta to Prod is gated by an automated integration-test suite — this limits the blast radius of a bad deploy reaching real usage.
- Known external constraint: Splitwise's free tier caps expense creation at roughly 3-4/day. The system doesn't build retry/queue logic around this for the first release (an explicit BRD non-goal); the CI/CD pipeline's own integration tests are designed to avoid tripping this limit themselves (see LLD).

## Environments & delivery

One AWS account, Beta and Prod distinguished by resource naming rather than account isolation. One monorepo, one CodePipeline: parallel per-package builds, automatic Beta deploy, an automated integration-test gate, then automatic Prod promotion with no manual approval step.

# Split Manager

Photograph a receipt or import a card statement, work out who owes what, and keep a
running ledger of it. Single-user by design: the people you split with are names in a
directory, not accounts — nobody else logs in.

Design docs live in [`docs/`](docs/): [`brd.md`](docs/brd.md) (what and why),
[`hld.md`](docs/hld.md) (architecture), [`lld.md`](docs/lld.md) (the detailed design, and
the manual verification checklist in §11), and [`tasks.md`](docs/tasks.md) (build status).

## Live environments

**Beta is the working environment.** All verification happens there. Prod exists in the
pipeline and promotes automatically, but it carries no users and is not verified — a
broken Prod is tolerable, a broken Beta is not.

| | Beta | Prod |
|---|---|---|
| App + API | https://d3frwzr61jzv4k.cloudfront.net | https://dpu5wjzhapzf8.cloudfront.net |
| Cognito pool | `us-east-1_tly0tdcrR` | `us-east-1_GuoDsn5FW` |
| App client | `4dtt6h8pnebl35iitm918tbph1` | `5jd6308r9ecbkaj1iv35kpl24s` |
| Hosted UI | `split-manager-beta-548171705631.auth.us-east-1.amazoncognito.com` | `split-manager-prod-548171705631.auth.us-east-1.amazoncognito.com` |
| Ledger table | `split-manager-beta-ledger` | `split-manager-prod-ledger` |

The SPA and the API are same-origin: CloudFront serves the app at `/` and routes `/api/*`
to the ALB. That is not cosmetic — the ALB is plain HTTP, so an HTTPS page could not call
it directly at all without tripping mixed-content blocking.

## Signing in

Self-signup is off. Accounts are created by hand in the Cognito pool; Beta has exactly one
(`sagar21naidu@gmail.com`).

Login is authorization-code + PKCE against the Cognito hosted UI. The access token is held
**in memory** and the refresh token in `sessionStorage` — deliberately never
`localStorage`, because this is financial data and XSS persistence is the concern. A page
reload silently refreshes rather than bouncing you back through sign-in.

To add a user:

```sh
aws cognito-idp admin-create-user \
  --user-pool-id us-east-1_tly0tdcrR \
  --username you@example.com \
  --message-action SUPPRESS
```

Then set a permanent password with `admin-set-user-password --permanent`.

## Repository layout

```
api/        Spring Boot 3 / Java 21 — the whole backend
frontend/   React + TypeScript + Vite SPA
infra/      AWS CDK (TypeScript): pipeline, and per-env Auth/Data/Api/Frontend stacks
lambdas/    Reserved and empty — see "Deferred" in docs/tasks.md
docs/       BRD, HLD, LLD, task list
```

## Building locally

```sh
# API — compiles and runs the unit tests
cd api && mvn test

# Frontend — type-checks and builds
cd frontend && npm ci && npm run build

# Infra — synthesises the CloudFormation templates
cd infra && npm ci && npx cdk synth
```

There is no local end-to-end setup. The API needs DynamoDB, S3, Textract, and a Cognito
issuer, so running the stack against real AWS in Beta is the supported path; the unit
tests cover the logic that is worth testing without them.

## Deploying

Everything ships through the `split-manager-pipeline` CodePipeline: push to `main` →
Source → Synth → SelfMutate → Assets → Beta → Prod. There is no promotion gate; Prod
follows Beta automatically.

```sh
# Watch a run
aws codepipeline get-pipeline-state --name split-manager-pipeline \
  --query "stageStates[].{stage:stageName,status:latestExecution.status}" --output table
```

### Pushes do not currently start a run

Known issue, cause identified. The **AWS Connector for GitHub** is present as an
*authorized OAuth app* but was never *installed* as a GitHub App on the account. Those are
different grants: the OAuth authorization is what lets the connection read the repo (which
is why every manual run's Source stage succeeds), while the App installation is what
delivers push events to AWS. Without it, `DetectChanges: true` has nothing to listen to.

Until the App is installed and bound to the connection, start runs by hand:

```sh
aws codepipeline start-pipeline-execution --name split-manager-pipeline
```

To fix it: AWS Console → Developer Tools → Settings → Connections → `split-manager-github`
→ install the app for `Sagar21999/SplitManagerApp`. Do it from the AWS side so the
installation binds to the existing connection and the ARN in `infra/lib/pipeline-stack.ts`
stays valid. Recreating the connection would change that ARN and require a code change.

Note that a run which changes the pipeline itself will self-mutate at `UpdatePipeline` and
restart as a **new execution**, cancelling the one you started. That is expected, not a
failure.

## Runbook

**Verify auth is still closed after every deploy** (BRD FR25). Unauthenticated:

```sh
curl -o /dev/null -s -w "%{http_code}\n" https://d3frwzr61jzv4k.cloudfront.net/api/transactions   # 401
curl -o /dev/null -s -w "%{http_code}\n" https://d3frwzr61jzv4k.cloudfront.net/api/balances       # 401
curl -s https://d3frwzr61jzv4k.cloudfront.net/api/actuator/health                                  # {"status":"UP"}
```

Because every non-health route requires a JWT, an unknown path also returns 401 — so a 401
confirms a route is protected but does not by itself prove it exists. Confirm new
endpoints signed in.

**Statement files are not retained.** An upload is written to the statements bucket, parsed,
and deleted in the same request (in a `finally`, so a failed parse still deletes). The
bucket's one-day lifecycle rule is a backstop for a crash mid-request, not the mechanism.
Receipt *images* are retained; statements are not. To confirm the bucket really is
emptying:

```sh
aws s3 ls s3://split-manager-beta-statements-548171705631/statements/
```

**If an imported statement comes out inverted** — every row counted as a credit, or nothing
imported at all — the issuer profile's `debitsArePositive` is the wrong way round. It is a
one-line fix in `api/src/main/resources/issuer-profiles.yml`. Issuers genuinely disagree
about the sign convention, and a profile whose columns do not match the uploaded file is
dropped entirely in favour of header inference, so picking the wrong issuer degrades
rather than corrupts.

**Duplicate warnings are warnings.** Nothing is ever auto-merged. The same shop for the
same amount two days running is a real second charge, and silently swallowing it would be
a worse failure than a warning you dismiss.

## Logs

The API's log group has a CDK-generated name that changes if the stack is replaced, so
look it up rather than memorising it:

```sh
LOGS=$(aws logs describe-log-groups \
  --query "logGroups[?starts_with(logGroupName,'Beta-ApiStack-ApiTaskDefinition')].logGroupName" \
  --output text)
aws logs tail "$LOGS" --follow
```

The API runs on ECS Fargate behind an ALB; the CloudFront distribution fronts both it and
the SPA's S3 origin.

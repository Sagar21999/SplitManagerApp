# Split Manager — Business Requirements Document (BRD)

*Companion documents: `hld.md` (architecture) and `lld.md` (implementation detail). This document intentionally ignores technology choices, hosting, and infrastructure — those live in the HLD/LLD.*

*Revision note: this is v2. The original scope — a transient, single-receipt split calculator that produced text to paste into Splitwise — is preserved in git history at commit 6e814bb. v2 turns the tool into a persistent personal ledger with two input paths (receipts and bank/card statements) and adds authentication. The section "What changed in v2 and why" records the reasoning behind each change.*

## Why we're building this

Splitting a group meal or shared purchase means manually reading every line off a receipt and typing it in somewhere. That friction is high enough that people default to a lazy equal split even when it isn't fair — someone ordered the $4 side salad, someone else the $22 steak, and both pay the same because nobody wanted to do the arithmetic.

But the deeper problem isn't the arithmetic. It's the *forgetting*. A tool you have to remember to open at the table only ever catches the expenses you thought about in the moment. The ones that quietly go unsplit — the Uber someone else should have shared, the group grocery run, the work trip transit fare that was never claimed — are invisible, and they are where the money actually leaks.

Split Manager attacks both. It removes the manual entry (photograph the receipt), and it removes the forgetting (periodically import a bank or card statement and be *shown* the transactions that probably should have been split or claimed).

## Business goals

- Eliminate manual line-item entry as the reason to avoid an itemized split.
- Make the itemized/fair split the *default* choice, not the effortful one.
- Catch expenses that should have been split or reimbursed but were forgotten, by reviewing statements after the fact rather than relying on in-the-moment capture.
- Be the user's own system of record for who owes what — not a shared, multi-party app.

## Target user

**Exactly one user: the author, for their own personal expense management.** This is not a commercial product, and it is deliberately not multi-user. Other people appear in the system only as *names on a transaction* — they have no accounts, no logins, no visibility, and receive nothing from the system automatically.

## Relationship to Splitwise

Split Manager **replaces Splitwise as the user's own record** of what has been split and who owes what.

Splitwise remains a **downstream, manual, non-blocking destination**. After finalizing a transaction here, the user may choose to also enter it into Splitwise — typically so the other party sees it in a tool they already have — and then marks the transaction "externally added" in Split Manager. Nothing in Split Manager waits on, depends on, or is invalidated by that step. A transaction that is never added to Splitwise is still a complete and valid record.

Splitwise's public API is no longer available, so this handoff is manual by necessity rather than by choice. That is acceptable precisely because it is non-blocking.

## User needs

- **Fast.** From receipt photo to a finalized split in a couple of taps, ideally at the table.
- **Accurate.** Correct per-person amounts, with tax and tip proportioned fairly.
- **Trustworthy.** This moves real money between real people. Nothing is finalized unreviewed — every extracted or suggested value is visible and correctable first.
- **Complete.** Expenses that should have been split are surfaced from statements, not left to memory.
- **Durable.** A permanent record of every transaction and its current status, queryable later.
- **Low friction on repeat use.** The people involved are remembered between transactions, so adding a regular participant is one tap rather than retyping a name.
- **Private.** This is a permanent store of the user's financial data. It must not be readable by anyone else.

## Functional requirements

### A. Receipt capture and parsing

1. The user can upload a photo of a receipt and submit it for processing.
2. The system automatically extracts the merchant name, each line item (name and price), tax, and total from the photo.
3. The user can review every extracted field and correct any of it — fix a misread price or name, add a missed item, remove an extra one — before anything is finalized.
4. The user can enter or adjust a tip, since tips are frequently not printed on the receipt.

### B. Splitting

5. The user records **who paid** for the transaction. Amounts owed are relative to that payer.
6. The user selects the people involved in a transaction, drawing from a saved directory of previously-used people (FR13) or by adding a new name inline.
7. The user can split a transaction by any of these modes:
   - **Equally** across everyone involved.
   - **By shares/units** — whole-number weights per person (e.g. 2 shares to one person, 1 to another).
   - **By ratio/percentage** — explicit percentages summing to 100.
   - **By exact amounts** — the user types each person's amount directly.
   - **By item** — assigning each line item to the people who shared it, with tax and tip distributed in proportion to each person's share of the subtotal.
8. The computed per-person amounts always sum exactly to the transaction total, with any rounding remainder absorbed by the payer.
9. The user sees a per-person breakdown and a shareable text summary for each transaction, formatted for manual entry elsewhere.

### C. The ledger

10. Every finalized transaction is stored permanently and remains retrievable. Transactions are not transient and do not expire.
11. The user can view a list of all transactions, most recent first, and open any one to see its full detail and summary.
12. The user can see, per person, the running net balance across all open transactions — who owes the user and how much.
13. The system maintains a **directory of people** the user has previously split with. A name is saved automatically the first time it is used and offered for quick selection on subsequent transactions. The user can add, rename, and remove entries.
14. Each transaction carries a status the user controls:
    - **Draft** — imported from a statement, not yet reviewed and confirmed.
    - **Open** — finalized and outstanding.
    - **Externally added** — the user has manually entered it into Splitwise (or otherwise handed it off) and considers it dispatched. This is a status update only; it triggers no external call.
    - **Settled** — the money has actually been received.

### D. Statement import

15. The user can upload a bank or credit card statement in **either CSV or PDF** format.
16. The system extracts the individual transactions (date, merchant/description, amount) from the statement.
17. The system identifies which of those transactions are **likely candidates** for splitting or reimbursement, and presents them for review rather than acting on them automatically.
18. The user reviews each candidate and can edit it, confirm it into a real transaction (proceeding through the normal split flow), or dismiss it.
19. The system **detects duplicates** — a statement transaction matching an already-recorded transaction is flagged rather than silently added. Matching is on merchant + date + amount, falling back to date + amount where the merchant string differs.
20. Raw uploaded statement files are deleted after extraction; only the extracted transaction data is retained.

### E. Work reimbursements

21. Every transaction has a **type**: a *split* (shared with other people) or a *reimbursement* (a work expense such as Uber or transit, to be claimed from the user's employer).
22. Reimbursement transactions follow the same import, review, and status lifecycle as splits, but have no participants and no split computation — only an amount, a merchant, a date, and a status.
23. The user can view reimbursements as a filtered list and produce a summary suitable for submitting an expense claim.

### F. Access control

24. Access to the entire application requires authentication. There is exactly one account.
25. No transaction, person, balance, or statement data is reachable without a valid authenticated session.

## Non-goals (explicitly out of scope)

- **Multi-user support of any kind.** Other people never get accounts, logins, or visibility. They are names on a record.
- Notifying, reminding, or messaging the other party — the user does this manually, outside the system.
- Automatic posting to Splitwise or any other third-party service (no usable public API exists).
- Looking up itemized receipt data from a merchant by bill number — no such general mechanism exists. See "What changed in v2 and why."
- Custom uneven fractional shares on a *single line item* (e.g. "I had 2/3 of this appetizer"). Whole-transaction share/ratio splitting is supported; per-item weighting is not.
- Automatic expense categorization beyond the split-candidate and reimbursement classification in FR17 and FR21.
- Direct bank connectivity (Plaid or equivalent). Statement import is a manual file upload by design — it avoids credential sharing and per-connection cost.
- Multi-currency. All amounts are assumed to be in a single currency.
- Spending-pattern reporting and analytics.

## Future scope (ideas, not committed)

- Decoding structured invoice data from receipt QR codes where fiscal e-invoicing regimes provide them — a genuine "read the real data instead of OCR'ing it" path, unlike merchant lookup.
- Ingesting emailed receipts, which arrive already structured.
- Surfacing per-field parse confidence so the user knows at a glance what most likely needs a second look.
- Spending-pattern reporting built on the transaction history.
- Automatic posting to a third-party expense service, if one ever offers a usable public API.

## What changed in v2 and why

**Merchant lookup by bill number was considered and rejected.** The idea was to read a bill number off the receipt and fetch authoritative itemized data from the merchant, using OCR only as a fallback. There is no general mechanism for this: no universal registry maps a bill number to merchant data, and POS vendors gate digital receipts to the merchant's own account rather than to whoever holds the paper. Building a speculative lookup layer with a near-zero hit rate in front of an OCR path that already works is cost without return. The salvageable version of the idea — decoding receipt QR codes where fiscal e-invoicing provides structured payloads — is recorded under Future scope.

**Transient sessions became a permanent ledger.** v1 deliberately kept nothing: each receipt was a session that expired on a timer. That made the tool a calculator you had to remember to use. Persistence is what enables the ledger, balances, statuses, the people directory, and duplicate detection — all of which follow from it.

**Authentication moved from an accepted gap to a hard requirement.** v1 ran without auth on the reasoning that session data was anonymous, transient, and self-deleting, so the blast radius of a leak was one receipt. Persisting a permanent transaction ledger and importing bank statements inverts that entirely: an unauthenticated endpoint would expose the user's complete financial history. Auth is now FR24/FR25 and is a prerequisite for statement import shipping.

**Statement import was promoted from "future scope" to core.** It was listed in v1 as speculative bank reconciliation. It is now the feature that addresses the forgetting problem, which is the larger of the two problems named in "Why we're building this."

**Deduplication was added as an explicit requirement.** It was not in the original pivot description but falls directly out of having two input paths that can describe the same purchase — upload a dinner receipt, then upload the statement containing that same charge three weeks later. Without FR19 the ledger silently accumulates doubles.

**Reimbursements are a transaction type, not a separate feature.** Uber and transit claims share the statement input, the review UI, and the status lifecycle with splits. The only differences are the absence of participants and the output format. Modeling this as a `type` discriminator from the start keeps it nearly free; bolting it on later would force a data migration.

**"Settled" status was added.** The original requirement named "externally added" as the terminal state. But once the system holds running balances (FR12), there must be a way for a balance to return to zero when money is actually received — otherwise every transaction stays outstanding forever. "Externally added" (dispatched to Splitwise) and "settled" (paid) are genuinely different events, so they are separate statuses.

## Success criteria

- A real, messy receipt goes from photo to a correct, finalized, permanently-recorded split without the user retyping a single line item.
- A statement import surfaces at least one expense the user had genuinely forgotten to split or claim — the feature pays for itself the first time this happens.
- Duplicate charges arriving via both input paths are caught, not silently doubled.
- The user can answer "who owes me what right now?" from the app, without opening Splitwise.
- The tool is trustworthy and fast enough to become the default way of splitting a bill, replacing the lazy equal-split habit it was built to fix.

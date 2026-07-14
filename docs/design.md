# Split Manager — High-Level Design (v2)

*Started fresh 2026-07-14 at the user's request ("starting from scratch — first a high level design doc"). This replaces the v1 design doc, which went through three platform pivots (Cloudflare -> Vercel/Postgres -> AWS/DynamoDB) before the user asked for a full reset. The v1 docs are preserved in the Claude project's `archive/` docs for reference, but nothing in them should be treated as settled here — this doc reopens everything except the core product idea, which the user confirmed stays the same.*

## Purpose

A personal tool that goes from "photo of a receipt" to "correctly itemized expense on Splitwise," without manually re-typing every line item. The recurring annoyance it solves: splitting a group meal or shared purchase in Splitwise today means manually reading every line off a paper or emailed receipt and typing it in — slow enough that people default to a lazy equal split even when it isn't fair (someone got the $4 side salad, someone else got the $22 steak).

Longer-term direction (not committed to, just the shape this could grow into): a personal audit trail of every split, spending-pattern tracking over time, and reconciliation against bank/card statements to catch splits that were never entered. Possibly, eventually, something other people could use too — but that's explicitly not a P0/P1 requirement, just a constraint on not painting the architecture into a single-user-only corner.

## Core user flow

1. User photographs a receipt (most likely right at the table, right after paying).
2. That photo gets to a small web page that shows the parsed items.
3. A vision-capable model reads the photo and extracts: merchant name, line items (name + price), tax, tip (often not printed — added at the terminal or by hand, so this needs to be treated as usually-absent, not a parsing failure), and total.
4. The user reviews and corrects the parse before anything is final — this involves real money, so nothing should be trusted blindly. Fix a misread price or name, add or remove a line.
5. The user enters/adjusts the tip if it wasn't on the receipt (quick presets like 18/20/25%, plus manual override).
6. The user picks who was there. This needs to support both "an existing Splitwise group" and "an ad hoc set of individual friends not sharing a group" — real usage is a mix of both, not just one.
7. The user picks a split mode:
   - **Equal** — total / N people.
   - **By item** — tap each item, choose who's sharing it; shared items split evenly among sharers; tax and tip get prorated by each person's share of the subtotal; any leftover rounding cent(s) default to whoever paid.
8. The user confirms, and the app calls Splitwise's API to create the itemized expense — receipt photo attached, correct group/friend context, correct per-person amounts.
9. A plain success/failure confirmation is shown.

**How this gets triggered from a phone** is an open question this time around (see "Open questions" below) — v1 assumed an iOS Shortcut specifically, which is worth reconsidering rather than carrying forward automatically.

### Why a web page for the split-assignment step, not a fully native experience?

Assigning individual line items to different people needs a real tap-to-assign interface — a list of checkboxes per item, running per-person totals updating live. That's meaningfully easier to build well as a small web UI than trying to recreate in whatever native capture mechanism triggers the flow. The capture/trigger step and the assignment step can be different technologies as long as the handoff between them is fast.

## P0 scope (what a first working version needs)

1. **Capture -> parse:** photo goes in, a vision model extracts merchant/items/tax/tip/total as structured data.
2. **Review & correct:** editable items before anything is submitted, plus manual tip entry/adjustment.
3. **Who was there:** supports both an existing Splitwise group and ad hoc individual-friend selection, pulled live from Splitwise.
4. **Split by item:** tap-to-assign, shared items split evenly among sharers, tax/tip prorated by subtotal share, rounding remainder to the payer.
5. **Equal split:** simple total / N.
6. **Post to Splitwise:** one-time app registration + personal API key (see "Splitwise API — verified facts" below), `create_expense` with the receipt image attached. All development/testing against a dummy Splitwise contact or private test group, never real friend groups, until the flow is solid (there's no Splitwise sandbox — this matters, see below).
7. **Confirmation:** plain success/failure message.

Explicitly out of scope for a first version: percentage split mode, retry/queue logic for Splitwise's daily rate limit, custom (non-even) fractions for shared items, category auto-detection, any kind of permanent history/audit trail (P0 is fine being stateless/short-lived per receipt).

## Ideas for later (not scoped, not being designed against yet)

Kept intentionally vague at this stage — these shape "don't paint the P0 architecture into a corner," not "build this now":

- Percentage split mode (each person assigned a %, not just equal or by-item).
- A retry/queue mechanism for Splitwise's free-tier daily expense-creation limit.
- A permanent local record of every receipt processed (image, parse, corrections, computed split, resulting Splitwise expense ID) — the user's own source of truth independent of Splitwise.
- Spending-pattern reporting over that history (by month, merchant, category, who you split with most).
- Bank/card statement reconciliation — upload a statement, cross-reference against the local history by date+amount (not merchant name — statement merchant strings are often garbled), flag anything that looks like a missed split.
- Multi-user support, if this ever goes beyond personal use.

## Splitwise API — verified facts (carried forward from v1, these are facts not decisions)

- **Self-serve, no approval wait for personal use.** Register an app at https://secure.splitwise.com/apps to get a consumer key/secret immediately — commercial integrations need to email Splitwise first, personal/non-commercial use doesn't. Source: dev.splitwise.com.
- **A personal API key** (bearer token) is enough for a single-user tool — Splitwise's docs describe it as "for testing purposes" distinct from full OAuth, likely fine at personal volume but not confirmed production-safe for continuous long-term use. Full OAuth2 is the fallback if this turns out flaky, and is also the right move if this ever supports multiple users.
- **Receipt image attachment works but is undocumented in the official OpenAPI spec** — confirmed by a Splitwise maintainer in a GitHub thread (splitwise/api-docs issue #21): the request must be `multipart/form-data`, not JSON, for the `receipt` field to be accepted.
- **Free tier is enough — Pro is not required.** What Splitwise gates behind Pro is their own in-app OCR/itemization feature; this project does its own parsing and only needs the basic "create an expense with custom per-person amounts" call, which is a long-standing free-tier capability.
- **Known free-tier limit:** a daily cap on expense creation (~3-4/day per user reports).
- **No sandbox/test-mode environment exists.** All testing happens against a real, live Splitwise account — a dummy contact or private test group is a real prerequisite before testing expense creation, not a nice-to-have.
- Previously verified live (before the reset): a read-only `get_friends` call against a real account returned the account's actual friends list correctly, confirming the auth model works as documented. This fact survives the reset (it's just confirmation the API behaves as documented); the credential itself may or may not still be valid/in-use going forward.

## Open questions (deliberately not decided in this doc)

This is a high-level design doc, not a build plan — the following are flagged as things to work out in a follow-up conversation before any implementation starts, rather than assumed:

- **What triggers the flow from a phone.** v1 assumed an iOS Shortcut (share-sheet trigger, native HEIC->JPEG conversion, "Get Contents of URL" to POST the photo). Worth reconsidering fresh — alternatives include a PWA with a share target, a simple "open camera, upload here" web page, or something else. Platform reach (iOS-only vs. cross-platform) is part of this decision.
- **Frontend/backend framework and hosting.** Explicitly reopened — nothing about Next.js, Vercel, or AWS should be assumed. Worth deciding based on actual constraints (personal use only vs. eventual sharing, budget, how much infrastructure the user wants to manage) rather than carrying forward momentum from before.
- **Storage.** Whatever P0 needs (short-lived parsed-receipt sessions) is a small problem regardless of backend choice; the bigger question — a relational store vs. a key-value/document store vs. something else — is worth deciding once P1's shape (history, reporting, reconciliation) is more concrete, not necessarily up front.
- **CI/CD.** Whether this needs a formal pipeline at all for a single-developer personal project, and if so what kind, is worth asking rather than assuming — this was a significant source of rework last time (a mid-build pivot to "fully CI/CD on AWS" after infrastructure had already been built once for a different stack).
- **What happens to the previous AWS-pivot codebase** (in the `Sagar21999/SplitManager` GitHub repo). Not touched as part of this doc reset. Worth an explicit decision (start clean in this new `SplitManagerApp` repo, pull anything worth keeping, or something else) once the new design is further along.

## What this doc deliberately does not contain

Tech stack, hosting, phased build plan, time estimates — all of that lived in a companion "build plan" doc last time and will again once the open questions above are resolved. Keeping this doc to the product shape on purpose, so it doesn't quietly re-anchor the next stack conversation the way carrying forward "Next.js from P0" did last time.

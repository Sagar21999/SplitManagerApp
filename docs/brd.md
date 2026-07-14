# Split Manager — Business Requirements Document (BRD)

*Companion documents: `hld.md` (architecture) and `lld.md` (implementation detail). This document intentionally ignores technology choices, hosting, and infrastructure — those live in the HLD/LLD.*

## Why we're building this

Splitting a group meal or shared purchase on Splitwise today means manually reading every line off a paper or emailed receipt and typing it in by hand. That friction is high enough that people default to a lazy equal split even when it isn't fair — someone ordered the $4 side salad, someone else ordered the $22 steak, and both end up paying the same amount because nobody wanted to do the arithmetic.

Split Manager removes that friction: photograph the receipt, and the correct itemized expense ends up on Splitwise with almost no manual entry.

## Business goals

- Eliminate manual line-item entry as the reason people avoid itemized splits.
- Make the itemized/fair split the *default* choice, not the effortful one.
- Ship a personal tool first — single user, low stakes, fast iteration — with the shape of something that could later serve more than one person if it proves useful.

## Target user

Initially a single user, for their own personal group-expense splitting via Splitwise. Not a commercial product at this stage.

## User needs

- **Fast.** From receipt photo to a posted Splitwise expense in a couple of taps, ideally right at the table before everyone disperses.
- **Accurate.** Correct per-person amounts, including tax and tip proportioned fairly, not just an even split by default.
- **Trustworthy.** This moves real money between real people. The user must be able to see and correct anything the system got wrong before it's final — nothing should post to Splitwise unreviewed.
- **Flexible.** Works whether the group is an existing Splitwise group or a one-off set of friends who don't share a group.
- **Low friction.** Works from a phone, from the photo-sharing action people already use, without opening a separate app and re-entering context.

## Functional requirements

1. The user can capture a photo of a receipt and submit it for processing.
2. The system automatically extracts the merchant name, each line item (name and price), tax, and total from the photo.
3. The user can review every extracted field and correct any of it — fix a misread price or name, add a missed item, remove an extra one — before anything is finalized.
4. The user can enter or adjust a tip amount, since tips are frequently not printed on the receipt (added at the register or added later by hand).
5. The user can choose who was involved in the purchase: either an existing Splitwise group, or an ad hoc set of individual friends not sharing a group.
6. The user can split the total two ways:
   - **Equally** across everyone involved.
   - **By item** — assigning each line item to the specific people who shared it, with tax and tip distributed fairly based on each person's share of the subtotal.
7. The user confirms the finalized split, and the system posts a single itemized expense to Splitwise, with the receipt photo attached, correctly reflecting who owes what.
8. The user receives a clear, simple confirmation that the expense was successfully created (or a clear indication if it failed).

## Non-goals (explicitly out of scope for the first release)

- Percentage-based (non-equal, non-item) splits.
- Automatic retry or queuing logic for when Splitwise's daily expense-creation limit is hit.
- Custom uneven fractional splits on a shared item (e.g., "I had 2/3 of this appetizer").
- Automatic expense categorization.
- Any kind of permanent history, audit trail, or reporting — each session is transient by design for the first release.

## Future scope (ideas, not committed)

- A permanent personal record of every receipt processed, with the original parse, the user's corrections, the computed split, and the resulting Splitwise expense.
- Spending-pattern reporting built on that history.
- Reconciliation against bank or card statements to catch splits that were started but never finished.
- Support for more than one user.
- Surfacing how confident the system is about each extracted field, so the user knows at a glance what's most likely to need a second look.

## Success criteria

- A real, messy, real-world receipt can go from photo to a correctly posted Splitwise expense without the user manually re-typing a single line item.
- The tool is trustworthy and fast enough that it becomes the user's default way of splitting a bill, replacing the lazy equal-split habit it was built to fix.

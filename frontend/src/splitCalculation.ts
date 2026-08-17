import type { ReceiptItem } from './types';

/**
 * TS port of api/.../service/SplitCalculationService.java, for the live client-side
 * preview (see docs/lld.md §5-§6). Uses plain floating-point cents-rounding rather than
 * BigDecimal, since this is a preview only — the amounts computed here are what actually
 * get submitted to POST /finalize-split (see docs/lld.md gap note: the API does not
 * independently recompute/verify shares server-side today).
 */

function roundToCents(value: number): number {
  return Math.round(value * 100) / 100;
}

export function computeEqualSplit(
  total: number,
  participantIds: string[],
): Record<string, number> {
  const n = participantIds.length;
  const baseShare = Math.floor((total / n) * 100) / 100;

  const shares: Record<string, number> = {};
  for (const id of participantIds) {
    shares[id] = baseShare;
  }

  const distributed = roundToCents(baseShare * n);
  const remainderCents = Math.round((total - distributed) * 100);

  for (let i = 0; i < remainderCents; i++) {
    const id = participantIds[i % n];
    shares[id] = roundToCents(shares[id] + 0.01);
  }

  return shares;
}

export function computeItemSplit(
  items: ReceiptItem[],
  itemAssignments: Record<string, string[]>,
  tax: number,
  tip: number,
  payerId: string,
): Record<string, number> {
  const subtotal = items.reduce((sum, item) => sum + item.price, 0);

  const participantSubtotals: Record<string, number> = {};
  for (const item of items) {
    const sharers = itemAssignments[item.id] ?? [];
    if (sharers.length === 0) continue;
    const perPersonShare = item.price / sharers.length;
    for (const participantId of sharers) {
      participantSubtotals[participantId] = (participantSubtotals[participantId] ?? 0) + perPersonShare;
    }
  }

  const owedShare: Record<string, number> = {};
  for (const [participantId, subtotalP] of Object.entries(participantSubtotals)) {
    const proportion = subtotal === 0 ? 0 : subtotalP / subtotal;
    const taxShare = tax * proportion;
    const tipShare = tip * proportion;
    owedShare[participantId] = roundToCents(subtotalP + taxShare + tipShare);
  }

  const total = subtotal + tax + tip;
  const totalOwed = Object.values(owedShare).reduce((sum, v) => sum + v, 0);
  const remainder = roundToCents(total - totalOwed);
  owedShare[payerId] = roundToCents((owedShare[payerId] ?? 0) + remainder);

  return owedShare;
}

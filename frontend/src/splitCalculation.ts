import type { LineItem, SplitDefinition } from './types';

/**
 * Client-side mirror of the API's SplitCalculationService (LLD 5), for live preview only.
 *
 * The server recomputes on finalize and is the source of truth — the frontend never sends
 * amounts the API accepts unverified. This exists so the user sees numbers update as they
 * drag assignments around, without a round trip per keystroke.
 *
 * Kept structurally identical to the Java implementation on purpose: resolve weights,
 * distribute, then hand the rounding remainder to the payer. If the two drift, the preview
 * lies about what finalize will produce, which is worse than having no preview.
 */

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

export interface SplitPreview {
  shares: Record<string, number>;
  error: string | null;
}

export function computeSplitPreview(
  definition: SplitDefinition,
  total: number,
  items: LineItem[],
): SplitPreview {
  const participants = definition.participantIds ?? [];
  if (participants.length === 0) {
    return { shares: {}, error: 'Add at least one participant.' };
  }
  if (!(total > 0)) {
    return { shares: {}, error: 'Total must be greater than zero.' };
  }

  let weights: Record<string, number>;

  switch (definition.mode) {
    case 'EQUAL':
      weights = Object.fromEntries(participants.map((id) => [id, 1]));
      break;

    case 'SHARES':
    case 'PERCENTAGE':
    case 'EXACT': {
      const provided = definition.weights ?? {};
      const missing = participants.find((id) => provided[id] === undefined);
      if (missing) {
        return { shares: {}, error: 'Enter a value for everyone.' };
      }
      weights = Object.fromEntries(participants.map((id) => [id, Number(provided[id])]));
      const sum = Object.values(weights).reduce((a, b) => a + b, 0);

      if (definition.mode === 'PERCENTAGE' && Math.abs(sum - 100) > 0.001) {
        return { shares: {}, error: `Percentages must add up to 100 (currently ${round2(sum)}).` };
      }
      if (definition.mode === 'EXACT' && Math.abs(sum - total) > 0.005) {
        return {
          shares: {},
          error: `Amounts must add up to ${total.toFixed(2)} (currently ${sum.toFixed(2)}).`,
        };
      }
      if (sum <= 0) {
        return { shares: {}, error: 'Values must add up to more than zero.' };
      }
      break;
    }

    case 'BY_ITEM': {
      const assignments = definition.itemAssignments ?? {};
      if (items.length === 0) {
        return { shares: {}, error: 'Add some items first.' };
      }
      const subtotals: Record<string, number> = Object.fromEntries(participants.map((id) => [id, 0]));
      for (const item of items) {
        const sharers = assignments[item.id] ?? [];
        if (sharers.length === 0) {
          return { shares: {}, error: `Assign "${item.name || 'an item'}" to someone.` };
        }
        const perPerson = item.price / sharers.length;
        for (const id of sharers) {
          subtotals[id] = (subtotals[id] ?? 0) + perPerson;
        }
      }
      if (Object.values(subtotals).reduce((a, b) => a + b, 0) <= 0) {
        return { shares: {}, error: 'Assigned items must total more than zero.' };
      }
      weights = subtotals;
      break;
    }

    default:
      return { shares: {}, error: 'Pick a split mode.' };
  }

  const totalWeight = Object.values(weights).reduce((a, b) => a + b, 0);
  const shares: Record<string, number> = {};
  for (const id of participants) {
    shares[id] = round2(total * (weights[id] / totalWeight));
  }

  // Force the shares to sum to the total exactly. Independent rounding leaves a cent or
  // two of drift; the payer absorbs it, matching the server.
  const distributed = Object.values(shares).reduce((a, b) => a + b, 0);
  const remainder = round2(total - distributed);
  if (remainder !== 0) {
    const payerId = definition.payerId;
    if (payerId && shares[payerId] !== undefined) {
      shares[payerId] = round2(shares[payerId] + remainder);
    } else {
      let cents = Math.round(remainder * 100);
      const step = cents > 0 ? 0.01 : -0.01;
      for (let i = 0; i < Math.abs(cents); i++) {
        const id = participants[i % participants.length];
        shares[id] = round2(shares[id] + step);
      }
    }
  }

  return { shares, error: null };
}

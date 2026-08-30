package com.splitmanager.api.model;

/**
 * BRD FR7. All five reduce to one algorithm in {@code SplitCalculationService}: resolve a
 * weight per participant, distribute the total proportionally, give the rounding
 * remainder to the payer. The modes differ only in how weights are produced.
 */
public enum SplitMode {
  /** Every participant weighted 1. */
  EQUAL,
  /** Caller-supplied whole-number weights, e.g. A:2, B:1. */
  SHARES,
  /** Caller-supplied percentages, validated to sum to 100. */
  PERCENTAGE,
  /** Caller-supplied amounts, validated to sum to the total. Weights are the amounts. */
  EXACT,
  /** Weights derived from per-item assignment; tax and tip follow subtotal share. */
  BY_ITEM,
}

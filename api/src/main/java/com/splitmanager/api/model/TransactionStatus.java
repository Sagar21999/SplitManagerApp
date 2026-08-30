package com.splitmanager.api.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a transaction (BRD FR14).
 *
 * <p>EXTERNALLY_ADDED and SETTLED are genuinely different events and deliberately not
 * collapsed: the first means "I typed this into Splitwise", the second means "the money
 * arrived". Only the second should stop counting toward a balance.
 */
public enum TransactionStatus {
  /** Imported from a statement, not yet reviewed and confirmed. */
  DRAFT,
  /** Finalized and outstanding. */
  OPEN,
  /** Handed off to Splitwise (or elsewhere) by hand. Status only - no outbound call. */
  EXTERNALLY_ADDED,
  /** The money has actually been received. */
  SETTLED;

  private static final Set<TransactionStatus> FROM_DRAFT = EnumSet.of(OPEN);
  private static final Set<TransactionStatus> FROM_OPEN = EnumSet.of(EXTERNALLY_ADDED, SETTLED);
  private static final Set<TransactionStatus> FROM_EXTERNALLY_ADDED = EnumSet.of(SETTLED, OPEN);
  // Reopening a settled transaction is a correction path, not a normal flow, but
  // mis-clicking "settled" should not be permanent.
  private static final Set<TransactionStatus> FROM_SETTLED = EnumSet.of(OPEN, EXTERNALLY_ADDED);

  public boolean canTransitionTo(TransactionStatus next) {
    return switch (this) {
      case DRAFT -> FROM_DRAFT.contains(next);
      case OPEN -> FROM_OPEN.contains(next);
      case EXTERNALLY_ADDED -> FROM_EXTERNALLY_ADDED.contains(next);
      case SETTLED -> FROM_SETTLED.contains(next);
    };
  }

  /** Whether a transaction in this state still contributes to per-person balances. */
  public boolean countsTowardBalance() {
    return this == OPEN || this == EXTERNALLY_ADDED;
  }
}

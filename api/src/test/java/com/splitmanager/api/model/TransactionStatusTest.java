package com.splitmanager.api.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The status lifecycle from BRD FR14 / LLD 4.5. */
class TransactionStatusTest {

  @Test
  void draftOnlyOpens() {
    assertTrue(TransactionStatus.DRAFT.canTransitionTo(TransactionStatus.OPEN));
    assertFalse(TransactionStatus.DRAFT.canTransitionTo(TransactionStatus.EXTERNALLY_ADDED));
    assertFalse(TransactionStatus.DRAFT.canTransitionTo(TransactionStatus.SETTLED));
  }

  @Test
  void openCanBeDispatchedOrSettled() {
    assertTrue(TransactionStatus.OPEN.canTransitionTo(TransactionStatus.EXTERNALLY_ADDED));
    assertTrue(TransactionStatus.OPEN.canTransitionTo(TransactionStatus.SETTLED));
  }

  @Test
  void nothingReturnsToDraft() {
    // A draft is pre-finalization. Going back would strand a computed split on a record
    // that is supposed to have none.
    for (TransactionStatus from : TransactionStatus.values()) {
      assertFalse(
          from.canTransitionTo(TransactionStatus.DRAFT), from + " should not return to DRAFT");
    }
  }

  @Test
  void settledCanBeReopenedAsACorrection() {
    assertTrue(TransactionStatus.SETTLED.canTransitionTo(TransactionStatus.OPEN));
  }

  @Test
  void externallyAddedStillCountsTowardBalances() {
    // Handing a transaction to Splitwise does not mean the money arrived - that is what
    // SETTLED is for. Treating EXTERNALLY_ADDED as closed would silently zero out debts
    // that are still outstanding.
    assertTrue(TransactionStatus.EXTERNALLY_ADDED.countsTowardBalance());
    assertTrue(TransactionStatus.OPEN.countsTowardBalance());
    assertFalse(TransactionStatus.SETTLED.countsTowardBalance());
    assertFalse(TransactionStatus.DRAFT.countsTowardBalance());
  }
}

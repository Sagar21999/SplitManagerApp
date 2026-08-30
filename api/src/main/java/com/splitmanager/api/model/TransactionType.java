package com.splitmanager.api.model;

/** BRD FR21: what kind of record this is. Drives whether a split is computed at all. */
public enum TransactionType {
  /** Shared with other people; carries participants and a split. */
  SPLIT,
  /** A work expense (Uber, transit) claimed from the employer. No participants, no split. */
  REIMBURSEMENT,
}

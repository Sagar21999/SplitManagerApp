package com.splitmanager.api.model;

/** Shared constants for participant identity. */
public final class Participants {

  /**
   * The ledger owner's own id in participant lists and as a payer.
   *
   * <p>A literal rather than a Person row: the owner is not someone you can rename or
   * archive, and BalanceService needs to tell "my share" from "their share" without a
   * lookup. BRD FR12's balances are all relative to this id.
   */
  public static final String SELF = "SELF";

  private Participants() {}
}

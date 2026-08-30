package com.splitmanager.api.exception;

import com.splitmanager.api.model.TransactionStatus;

/** A status change the lifecycle in {@link TransactionStatus} does not allow. */
public class IllegalStatusTransitionException extends RuntimeException {
  public IllegalStatusTransitionException(TransactionStatus from, TransactionStatus to) {
    super("Cannot move a transaction from " + from + " to " + to + ".");
  }
}

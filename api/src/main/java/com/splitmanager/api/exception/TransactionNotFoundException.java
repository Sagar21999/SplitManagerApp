package com.splitmanager.api.exception;

public class TransactionNotFoundException extends RuntimeException {
  public TransactionNotFoundException(String transactionId) {
    super("No transaction with id " + transactionId);
  }
}

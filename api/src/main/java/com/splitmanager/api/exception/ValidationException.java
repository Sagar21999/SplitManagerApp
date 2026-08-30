package com.splitmanager.api.exception;

/** A request that is well-formed but semantically invalid - percentages that do not sum
 * to 100, an unassigned line item, an empty participant list. */
public class ValidationException extends RuntimeException {
  public ValidationException(String message) {
    super(message);
  }
}

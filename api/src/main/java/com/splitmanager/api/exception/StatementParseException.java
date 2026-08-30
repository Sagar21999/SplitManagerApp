package com.splitmanager.api.exception;

/**
 * The statement file could not be read at all — wrong format, unreadable header, no
 * recognisable columns.
 *
 * <p>Not thrown for a partial parse: dropping some rows out of many is reported on the
 * import as a warning, because 40 usable rows beat an error message (LLD 10).
 */
public class StatementParseException extends RuntimeException {

  public StatementParseException(String message) {
    super(message);
  }
}

package com.splitmanager.api.exception;

public class StatementImportNotFoundException extends RuntimeException {

  public StatementImportNotFoundException(String importId) {
    super("No statement import with id " + importId + ".");
  }
}

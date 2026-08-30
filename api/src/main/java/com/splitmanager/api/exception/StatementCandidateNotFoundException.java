package com.splitmanager.api.exception;

public class StatementCandidateNotFoundException extends RuntimeException {

  public StatementCandidateNotFoundException(String candidateId) {
    super("No statement candidate with id " + candidateId + ".");
  }
}

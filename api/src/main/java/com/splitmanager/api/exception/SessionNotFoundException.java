package com.splitmanager.api.exception;

public class SessionNotFoundException extends RuntimeException {
  public SessionNotFoundException(String sessionId) {
    super("No session found with id " + sessionId);
  }
}

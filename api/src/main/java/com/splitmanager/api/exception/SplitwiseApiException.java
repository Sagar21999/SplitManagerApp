package com.splitmanager.api.exception;

public class SplitwiseApiException extends RuntimeException {
  public SplitwiseApiException(String message) {
    super(message);
  }

  public SplitwiseApiException(String message, Throwable cause) {
    super(message, cause);
  }
}

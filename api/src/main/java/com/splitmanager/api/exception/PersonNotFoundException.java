package com.splitmanager.api.exception;

public class PersonNotFoundException extends RuntimeException {
  public PersonNotFoundException(String personId) {
    super("No person with id " + personId);
  }
}

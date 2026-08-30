package com.splitmanager.api.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler({TransactionNotFoundException.class, PersonNotFoundException.class})
  public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
    return status(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Map<String, String>> handleValidation(ValidationException ex) {
    return status(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(IllegalStatusTransitionException.class)
  public ResponseEntity<Map<String, String>> handleIllegalTransition(
      IllegalStatusTransitionException ex) {
    // 409 rather than 400: the request is valid, the resource is just not in a state
    // that permits it - typically two tabs acting on the same transaction.
    return status(HttpStatus.CONFLICT, ex.getMessage());
  }

  private static ResponseEntity<Map<String, String>> status(HttpStatus code, String message) {
    return ResponseEntity.status(code).body(Map.of("error", message));
  }
}

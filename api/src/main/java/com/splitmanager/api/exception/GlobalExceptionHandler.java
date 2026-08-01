package com.splitmanager.api.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(SessionNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleSessionNotFound(SessionNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(SplitwiseApiException.class)
  public ResponseEntity<Map<String, String>> handleSplitwiseApiException(SplitwiseApiException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
  }
}

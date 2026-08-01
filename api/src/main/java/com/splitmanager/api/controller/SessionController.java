package com.splitmanager.api.controller;

import com.splitmanager.api.dto.SessionResponse;
import com.splitmanager.api.model.ReceiptSession;
import com.splitmanager.api.service.ReceiptSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

  private final ReceiptSessionService sessionService;

  public SessionController(ReceiptSessionService sessionService) {
    this.sessionService = sessionService;
  }

  @GetMapping("/session/{sessionId}")
  public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionId) {
    ReceiptSession session = sessionService.get(sessionId);
    return ResponseEntity.ok(
        new SessionResponse(
            session.getSessionId(),
            session.getStatus(),
            session.getMerchant(),
            session.getItems(),
            session.getTax(),
            session.getTip(),
            session.getTotal()));
  }
}

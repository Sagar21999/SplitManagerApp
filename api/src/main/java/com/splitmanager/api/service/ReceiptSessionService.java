package com.splitmanager.api.service;

import com.splitmanager.api.exception.SessionNotFoundException;
import com.splitmanager.api.model.ReceiptSession;
import com.splitmanager.api.model.SessionStatus;
import com.splitmanager.api.repository.ReceiptSessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReceiptSessionService {

  private static final String SINGLE_USER_ID = "single-user";

  private final ReceiptSessionRepository repository;
  private final long sessionTtlHours;

  public ReceiptSessionService(
      ReceiptSessionRepository repository,
      @Value("${split-manager.session-ttl-hours}") long sessionTtlHours) {
    this.repository = repository;
    this.sessionTtlHours = sessionTtlHours;
  }

  public ReceiptSession create(String imageS3Key, String contentType) {
    Instant now = Instant.now();
    ReceiptSession session = new ReceiptSession();
    session.setSessionId(UUID.randomUUID().toString());
    session.setUserId(SINGLE_USER_ID);
    session.setStatus(SessionStatus.PARSING);
    session.setCreatedAt(now);
    session.setExpiresAt(now.plus(sessionTtlHours, ChronoUnit.HOURS).getEpochSecond());
    session.setReceiptImageS3Key(imageS3Key);
    session.setReceiptImageContentType(contentType);
    repository.save(session);
    return session;
  }

  public ReceiptSession get(String sessionId) {
    return repository.findById(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
  }

  public void updateParsedFields(String sessionId, ParsedReceipt parsed) {
    ReceiptSession session = get(sessionId);
    session.setMerchant(parsed.merchant());
    session.setItems(parsed.items());
    session.setTax(parsed.tax());
    session.setTotal(parsed.total());
    session.setStatus(SessionStatus.PARSED);
    repository.update(session);
  }

  public void markParseFailed(String sessionId, String reason) {
    ReceiptSession session = get(sessionId);
    session.setStatus(SessionStatus.PARSE_FAILED);
    session.setFailureReason(reason);
    repository.update(session);
  }

  public void markSubmitted(String sessionId, String splitwiseExpenseId) {
    ReceiptSession session = get(sessionId);
    session.setStatus(SessionStatus.SUBMITTED);
    session.setSplitwiseExpenseId(splitwiseExpenseId);
    repository.update(session);
  }

  public void markFailed(String sessionId, String reason) {
    ReceiptSession session = get(sessionId);
    session.setStatus(SessionStatus.SUBMIT_FAILED);
    session.setFailureReason(reason);
    repository.update(session);
  }
}

package com.splitmanager.api.controller;

import com.splitmanager.api.client.SplitwiseRequestBuilder;
import com.splitmanager.api.dto.SplitwiseExpenseRequestDto;
import com.splitmanager.api.dto.SubmitExpenseRequest;
import com.splitmanager.api.dto.SubmitExpenseResponse;
import com.splitmanager.api.model.ReceiptSession;
import com.splitmanager.api.model.SessionStatus;
import com.splitmanager.api.service.ReceiptImageStore;
import com.splitmanager.api.service.ReceiptSessionService;
import com.splitmanager.api.service.SplitwiseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpenseController {

  private final ReceiptSessionService sessionService;
  private final SplitwiseService splitwiseService;
  private final SplitwiseRequestBuilder requestBuilder;
  private final ReceiptImageStore imageStore;

  public ExpenseController(
      ReceiptSessionService sessionService,
      SplitwiseService splitwiseService,
      SplitwiseRequestBuilder requestBuilder,
      ReceiptImageStore imageStore) {
    this.sessionService = sessionService;
    this.splitwiseService = splitwiseService;
    this.requestBuilder = requestBuilder;
    this.imageStore = imageStore;
  }

  @PostMapping("/submit-expense")
  public ResponseEntity<SubmitExpenseResponse> submitExpense(@RequestBody SubmitExpenseRequest request) {
    ReceiptSession session = sessionService.get(request.getSessionId());

    if (session.getStatus() == SessionStatus.SUBMITTED) {
      return ResponseEntity.ok(new SubmitExpenseResponse(true, session.getSplitwiseExpenseId(), null));
    }

    try {
      byte[] imageBytes = imageStore.download(session.getReceiptImageS3Key());
      String expenseId = splitwiseService.createExpense(session, request.getSplit(), imageBytes);
      sessionService.markSubmitted(session.getSessionId(), expenseId);
      return ResponseEntity.ok(new SubmitExpenseResponse(true, expenseId, null));
    } catch (Exception e) {
      sessionService.markFailed(session.getSessionId(), e.getMessage());
      return ResponseEntity.ok(new SubmitExpenseResponse(false, null, e.getMessage()));
    }
  }

  // Internal-only: builds the Splitwise request without sending it, so integ-tests
  // can verify request-construction without creating a real Splitwise expense.
  @PostMapping("/internal/submit-expense-dry-run")
  public ResponseEntity<SplitwiseExpenseRequestDto> submitExpenseDryRun(@RequestBody SubmitExpenseRequest request) {
    ReceiptSession session = sessionService.get(request.getSessionId());
    return ResponseEntity.ok(requestBuilder.build(session, request.getSplit()));
  }
}

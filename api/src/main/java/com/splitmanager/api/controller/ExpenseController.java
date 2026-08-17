package com.splitmanager.api.controller;

import com.splitmanager.api.dto.FinalizeSplitResponse;
import com.splitmanager.api.dto.SplitSummaryDto;
import com.splitmanager.api.dto.SubmitExpenseRequest;
import com.splitmanager.api.model.ReceiptSession;
import com.splitmanager.api.model.SessionStatus;
import com.splitmanager.api.service.ReceiptSessionService;
import com.splitmanager.api.service.SplitSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpenseController {

  private final ReceiptSessionService sessionService;
  private final SplitSummaryService splitSummaryService;

  public ExpenseController(ReceiptSessionService sessionService, SplitSummaryService splitSummaryService) {
    this.sessionService = sessionService;
    this.splitSummaryService = splitSummaryService;
  }

  @PostMapping("/finalize-split")
  public ResponseEntity<FinalizeSplitResponse> finalizeSplit(@RequestBody SubmitExpenseRequest request) {
    ReceiptSession session = sessionService.get(request.getSessionId());

    SplitSummaryDto summary = splitSummaryService.generateSummary(session, request.getSplit());
    if (session.getStatus() != SessionStatus.FINALIZED) {
      sessionService.markFinalized(session.getSessionId());
    }
    return ResponseEntity.ok(new FinalizeSplitResponse(true, summary, null));
  }
}

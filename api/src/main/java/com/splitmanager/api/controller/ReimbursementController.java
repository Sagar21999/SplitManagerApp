package com.splitmanager.api.controller;

import com.splitmanager.api.config.CurrentUser;
import com.splitmanager.api.dto.TransactionDto;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionType;
import com.splitmanager.api.service.SplitSummaryService;
import com.splitmanager.api.service.TransactionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRD FR23. A filtered view over the same ledger rather than a separate resource - the
 * only real difference from a split is the absence of participants and the output format.
 */
@RestController
@RequestMapping("/reimbursements")
public class ReimbursementController {

  private final TransactionService transactionService;
  private final SplitSummaryService summaryService;
  private final CurrentUser currentUser;

  public ReimbursementController(
      TransactionService transactionService,
      SplitSummaryService summaryService,
      CurrentUser currentUser) {
    this.transactionService = transactionService;
    this.summaryService = summaryService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ResponseEntity<List<TransactionDto>> list(@RequestParam(defaultValue = "200") int limit) {
    return ResponseEntity.ok(
        transactionService
            .list(currentUser.userId(), null, TransactionType.REIMBURSEMENT, limit)
            .stream()
            .map(TransactionDto::from)
            .toList());
  }

  /** Claim-ready text for pasting into an expense report. */
  @GetMapping("/summary")
  public ResponseEntity<Map<String, String>> summary(
      @RequestParam(defaultValue = "200") int limit) {
    List<Transaction> reimbursements =
        transactionService.list(currentUser.userId(), null, TransactionType.REIMBURSEMENT, limit);
    return ResponseEntity.ok(
        Map.of("summaryText", summaryService.generateReimbursementSummary(reimbursements)));
  }
}

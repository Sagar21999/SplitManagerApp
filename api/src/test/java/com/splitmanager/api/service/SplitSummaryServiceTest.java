package com.splitmanager.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitmanager.api.dto.SplitSummaryDto;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.Participants;
import com.splitmanager.api.model.SplitMode;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplitSummaryServiceTest {

  private final SplitSummaryService service = new SplitSummaryService();

  private static final Map<String, String> NAMES =
      Map.of("p-alice", "Alice", "p-bob", "Bob", "p-carol", "Carol");

  private Transaction transaction(String merchant, BigDecimal total, FinalizedSplit split) {
    Transaction t = new Transaction();
    t.setMerchant(merchant);
    t.setTotal(total);
    t.setTransactionDate(LocalDate.of(2026, 8, 30));
    t.setType(TransactionType.SPLIT);
    t.setFinalizedSplit(split);
    return t;
  }

  private FinalizedSplit split(String payerId, Map<String, BigDecimal> shares) {
    return new FinalizedSplit(SplitMode.EQUAL, shares, payerId, Instant.now());
  }

  @Test
  void breakdownMatchesTheComputedShares() {
    Map<String, BigDecimal> shares = new LinkedHashMap<>();
    shares.put(Participants.SELF, new BigDecimal("9.00"));
    shares.put("p-bob", new BigDecimal("9.00"));

    SplitSummaryDto summary =
        service.generateSummary(
            transaction("Diner", new BigDecimal("18.00"), split(Participants.SELF, shares)), NAMES);

    assertEquals(shares, summary.amountOwedByParticipant());
  }

  @Test
  void shareTextResolvesPersonIdsToNames() {
    Map<String, BigDecimal> shares = new LinkedHashMap<>();
    shares.put(Participants.SELF, new BigDecimal("9.00"));
    shares.put("p-bob", new BigDecimal("9.00"));

    String text =
        service
            .generateSummary(
                transaction("Diner", new BigDecimal("18.00"), split(Participants.SELF, shares)), NAMES)
            .shareText();

    assertTrue(text.contains("Diner"));
    assertTrue(text.contains("$18.00"));
    assertTrue(text.contains("You paid"));
    assertTrue(text.contains("Bob owes $9.00"));
    // Raw ids are an internal detail and must never reach text the user pastes elsewhere.
    assertFalse(text.contains("p-bob"));
  }

  @Test
  void payersOwnShareIsNotListedAsOwed() {
    // The payer already holds their own share; listing it reads as a debt to themselves
    // and is the most confusing thing to paste into Splitwise.
    Map<String, BigDecimal> shares = new LinkedHashMap<>();
    shares.put("p-alice", new BigDecimal("9.00"));
    shares.put("p-bob", new BigDecimal("9.00"));

    String text =
        service
            .generateSummary(
                transaction("Diner", new BigDecimal("18.00"), split("p-alice", shares)), NAMES)
            .shareText();

    assertTrue(text.contains("Alice paid"));
    assertFalse(text.contains("Alice owes"));
    assertTrue(text.contains("Bob owes $9.00"));
  }

  @Test
  void unfinalizedTransactionYieldsAnEmptySummary() {
    SplitSummaryDto summary =
        service.generateSummary(transaction("Cafe", new BigDecimal("10.00"), null), NAMES);

    assertTrue(summary.amountOwedByParticipant().isEmpty());
    assertEquals("", summary.shareText());
  }

  @Test
  void reimbursementSummaryTotalsTheClaim() {
    Transaction uber = new Transaction();
    uber.setType(TransactionType.REIMBURSEMENT);
    uber.setMerchant("Uber");
    uber.setTotal(new BigDecimal("24.50"));
    uber.setTransactionDate(LocalDate.of(2026, 8, 28));

    Transaction transit = new Transaction();
    transit.setType(TransactionType.REIMBURSEMENT);
    transit.setMerchant("Metro");
    transit.setTotal(new BigDecimal("3.25"));
    transit.setTransactionDate(LocalDate.of(2026, 8, 29));

    String text = service.generateReimbursementSummary(List.of(uber, transit));

    assertTrue(text.contains("Uber"));
    assertTrue(text.contains("Metro"));
    assertTrue(text.contains("Total: $27.75"));
  }
}

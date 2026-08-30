package com.splitmanager.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitmanager.api.model.CandidateClassification;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionStatus;
import com.splitmanager.api.model.TransactionType;
import com.splitmanager.api.parser.RawStatementRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatementClassificationServiceTest {

  private final StatementClassificationService service =
      new StatementClassificationService(new BigDecimal("40.00"));

  private static RawStatementRow row(String description, String amount) {
    return new RawStatementRow(LocalDate.of(2026, 8, 12), description, new BigDecimal(amount), false);
  }

  private static Transaction transaction(String merchant, TransactionType type, TransactionStatus status) {
    Transaction t = new Transaction();
    t.setMerchant(merchant);
    t.setType(type);
    t.setStatus(status);
    return t;
  }

  @Test
  void historyBeatsEveryOtherSignal() {
    // "UBER EATS" would otherwise trip the reimbursement keyword list. Having confirmed
    // it as a split once, the user should not have to correct it again.
    Map<String, TransactionType> history =
        service.buildHistory(List.of(transaction("Uber Eats", TransactionType.SPLIT, TransactionStatus.OPEN)));

    var result = service.classify(row("UBER EATS 8XKZ2", "62.10"), history);

    assertEquals(CandidateClassification.LIKELY_SPLIT, result.classification());
    assertEquals(0, new BigDecimal("0.95").compareTo(result.confidence()));
  }

  @Test
  void historyIgnoresDrafts() {
    // A draft is an unreviewed guess - very possibly one a previous import created.
    // Learning from it would let one misclassification reinforce itself.
    Map<String, TransactionType> history =
        service.buildHistory(
            List.of(transaction("Metro Transit", TransactionType.SPLIT, TransactionStatus.DRAFT)));

    assertTrue(history.isEmpty());
    assertEquals(
        CandidateClassification.LIKELY_REIMBURSEMENT,
        service.classify(row("METRO TRANSIT", "2.90"), history).classification());
  }

  @Test
  void historyPrefersTheMostRecentDecision() {
    // The list arrives newest-first, so the newer entry is the one that should stick.
    Map<String, TransactionType> history =
        service.buildHistory(
            List.of(
                transaction("Lyft", TransactionType.SPLIT, TransactionStatus.OPEN),
                transaction("Lyft", TransactionType.REIMBURSEMENT, TransactionStatus.SETTLED)));

    assertEquals(TransactionType.SPLIT, history.get("lyft"));
  }

  @Test
  void flagsTravelAsAReimbursement() {
    var result = service.classify(row("UBER *TRIP HELP.UBER.COM", "24.30"), Map.of());

    assertEquals(CandidateClassification.LIKELY_REIMBURSEMENT, result.classification());
    assertEquals(0, new BigDecimal("0.80").compareTo(result.confidence()));
  }

  @Test
  void flagsRestaurantsAsSplitsEvenWhenSmall() {
    var result = service.classify(row("TST* JOES PIZZA", "12.00"), Map.of());

    assertEquals(CandidateClassification.LIKELY_SPLIT, result.classification());
  }

  @Test
  void flagsAnythingOverTheThresholdAsASplit() {
    assertEquals(
        CandidateClassification.LIKELY_SPLIT,
        service.classify(row("SOME UNKNOWN SHOP", "88.00"), Map.of()).classification());
  }

  @Test
  void leavesSmallUnrecognisedChargesAlone() {
    // The conservative default. A wrongly-flagged row costs the user attention; an
    // unflagged one costs a manual add.
    var result = service.classify(row("CVS PHARMACY #4021", "9.40"), Map.of());

    assertEquals(CandidateClassification.UNLIKELY, result.classification());
    assertEquals(0, BigDecimal.ZERO.compareTo(result.confidence()));
  }
}

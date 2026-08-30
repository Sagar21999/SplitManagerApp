package com.splitmanager.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitmanager.api.model.DuplicateMatch;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeduplicationServiceTest {

  private static final String USER = "user-1";
  private static final LocalDate STATEMENT_DATE = LocalDate.of(2026, 8, 15);
  private static final BigDecimal AMOUNT = new BigDecimal("18.40");

  /**
   * Stands in for the GSI2 query. Real DynamoDB is not needed to exercise the date window
   * and the merchant scoring, which is all this service does.
   */
  private static class StubRepository {

    private final Map<LocalDate, List<Transaction>> byDate = new HashMap<>();

    void add(LocalDate date, Transaction transaction) {
      byDate.computeIfAbsent(date, d -> new ArrayList<>()).add(transaction);
    }

    TransactionRepository asRepository() {
      TransactionRepository repository = Mockito.mock(TransactionRepository.class);
      Mockito.when(repository.findByAmountAndDate(Mockito.any(), Mockito.any(), Mockito.any()))
          .thenAnswer(call -> byDate.getOrDefault(call.getArgument(2, LocalDate.class), List.of()));
      return repository;
    }
  }

  private static DeduplicationService serviceOver(StubRepository repository) {
    return new DeduplicationService(repository.asRepository());
  }

  private static Transaction transaction(String id, String merchant, LocalDate date) {
    Transaction t = new Transaction();
    t.setTransactionId(id);
    t.setMerchant(merchant);
    t.setTransactionDate(date);
    t.setTotal(AMOUNT);
    return t;
  }

  @Test
  void matchesAcrossTheSettlementLag() {
    // The receipt is dated three days before the statement posts it. Requiring an exact
    // date match would miss the most common duplicate there is.
    StubRepository repository = new StubRepository();
    LocalDate receiptDate = STATEMENT_DATE.minusDays(3);
    repository.add(receiptDate, transaction("t-1", "Blue Bottle Coffee", receiptDate));

    List<DuplicateMatch> matches =
        serviceOver(repository)
            .findMatches(USER, "SQ *BLUE BOTTLE COFFEE", STATEMENT_DATE, AMOUNT);

    assertEquals(1, matches.size());
    assertEquals("t-1", matches.get(0).getTransactionId());
    assertEquals(DuplicateMatch.MERCHANT_DATE_AMOUNT, matches.get(0).getMatchStrategy());
  }

  @Test
  void ignoresChargesOutsideTheWindow() {
    StubRepository repository = new StubRepository();
    LocalDate tooFar = STATEMENT_DATE.minusDays(4);
    repository.add(tooFar, transaction("t-1", "Blue Bottle Coffee", tooFar));

    assertTrue(
        serviceOver(repository)
            .findMatches(USER, "BLUE BOTTLE COFFEE", STATEMENT_DATE, AMOUNT)
            .isEmpty());
  }

  @Test
  void reportsAnUnrelatedMerchantAsAWeakMatch() {
    // Same amount, same day, different shop. Worth a glance, not worth a strong claim -
    // and never suppressed, because a real repeat charge looks exactly like this.
    StubRepository repository = new StubRepository();
    repository.add(STATEMENT_DATE, transaction("t-1", "Shell Gas Station", STATEMENT_DATE));

    List<DuplicateMatch> matches =
        serviceOver(repository)
            .findMatches(USER, "BLUE BOTTLE COFFEE", STATEMENT_DATE, AMOUNT);

    assertEquals(1, matches.size());
    assertEquals(DuplicateMatch.DATE_AMOUNT, matches.get(0).getMatchStrategy());
    assertEquals(0, new BigDecimal("0.50").compareTo(matches.get(0).getScore()));
  }

  @Test
  void sortsStrongMatchesFirst() {
    StubRepository repository = new StubRepository();
    repository.add(STATEMENT_DATE, transaction("t-weak", "Shell Gas Station", STATEMENT_DATE));
    repository.add(STATEMENT_DATE, transaction("t-strong", "Blue Bottle Coffee", STATEMENT_DATE));

    List<DuplicateMatch> matches =
        serviceOver(repository)
            .findMatches(USER, "BLUE BOTTLE COFFEE", STATEMENT_DATE, AMOUNT);

    assertEquals("t-strong", matches.get(0).getTransactionId());
    assertEquals("t-weak", matches.get(1).getTransactionId());
  }

  @Test
  void excludesTheTransactionBeingChecked() {
    // The receipt path checks a draft it has just written, which would otherwise be a
    // perfect match against itself.
    StubRepository repository = new StubRepository();
    repository.add(STATEMENT_DATE, transaction("t-self", "Blue Bottle Coffee", STATEMENT_DATE));

    assertTrue(
        serviceOver(repository)
            .findMatches(USER, "Blue Bottle Coffee", STATEMENT_DATE, AMOUNT, "t-self")
            .isEmpty());
  }

  @Test
  void carriesTheMatchedMerchantAndDateForDisplay() {
    StubRepository repository = new StubRepository();
    LocalDate receiptDate = STATEMENT_DATE.minusDays(1);
    repository.add(receiptDate, transaction("t-1", "Blue Bottle Coffee", receiptDate));

    DuplicateMatch match =
        serviceOver(repository)
            .findMatches(USER, "SQ *BLUE BOTTLE COFF", STATEMENT_DATE, AMOUNT)
            .get(0);

    assertEquals("Blue Bottle Coffee", match.getMerchant());
    assertEquals(receiptDate, match.getTransactionDate());
  }

  @Test
  void scoresProcessorPrefixesAndCaseAsTheSameMerchant() {
    assertEquals(1.0, DeduplicationService.similarity("blue bottle coffee", "blue bottle coffee"));
    assertTrue(DeduplicationService.similarity("blue bottle coff", "blue bottle coffee") >= 0.85);
    assertTrue(DeduplicationService.similarity("blue bottle coffee", "shell gas station") < 0.85);
  }

  @Test
  void returnsNothingWhenThereIsNoDateOrAmountToMatchOn() {
    DeduplicationService service = serviceOver(new StubRepository());

    assertTrue(service.findMatches(USER, "Anything", null, AMOUNT).isEmpty());
    assertTrue(service.findMatches(USER, "Anything", STATEMENT_DATE, null).isEmpty());
  }
}

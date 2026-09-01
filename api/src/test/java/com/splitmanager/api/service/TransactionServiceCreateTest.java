package com.splitmanager.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.splitmanager.api.exception.ValidationException;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionStatus;
import com.splitmanager.api.model.TransactionType;
import com.splitmanager.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The starting status of a newly created transaction.
 *
 * <p>Worth pinning because two entry paths now share this rule — hand entry and statement
 * confirmation — and they must agree. They previously did not: the statement path opened
 * reimbursements itself, so a hand-entered one would have been stranded as a DRAFT.
 */
class TransactionServiceCreateTest {

  private static final String USER = "user-1";

  private final TransactionRepository repository = Mockito.mock(TransactionRepository.class);
  private final TransactionService service =
      new TransactionService(repository, new SplitCalculationService());

  private Transaction create(TransactionType type) {
    return service.create(USER, type, "Uber", LocalDate.of(2026, 8, 30), new BigDecimal("24.30"), null);
  }

  @Test
  void aReimbursementIsBornOpen() {
    // Nothing is left to decide - the whole amount is claimed from the employer, and
    // there is no split to finalize. DRAFT only exits through finalizeSplit, which
    // refuses a REIMBURSEMENT, so a draft reimbursement could never leave that state.
    assertEquals(TransactionStatus.OPEN, create(TransactionType.REIMBURSEMENT).getStatus());
  }

  @Test
  void aSplitStaysADraftUntilItIsFinalized() {
    assertEquals(TransactionStatus.DRAFT, create(TransactionType.SPLIT).getStatus());
  }

  @Test
  void aReimbursementCountsTowardTheClaimImmediately() {
    assertEquals(true, create(TransactionType.REIMBURSEMENT).getStatus().countsTowardBalance());
  }

  @Test
  void rejectsATotalThatIsNotPositive() {
    assertThrows(
        ValidationException.class,
        () -> service.create(USER, TransactionType.SPLIT, "Nowhere", LocalDate.now(), BigDecimal.ZERO, null));
  }

  @Test
  void recordsStatementProvenanceWhenThereIsAny() {
    Transaction fromStatement =
        service.create(
            USER, TransactionType.SPLIT, "Blue Bottle", LocalDate.now(), new BigDecimal("18.40"), "import-1");

    assertEquals("import-1", fromStatement.getSourceStatementImportId());
    assertEquals(null, create(TransactionType.SPLIT).getSourceStatementImportId());
  }
}

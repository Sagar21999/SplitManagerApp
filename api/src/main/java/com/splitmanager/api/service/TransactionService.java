package com.splitmanager.api.service;

import com.splitmanager.api.exception.IllegalStatusTransitionException;
import com.splitmanager.api.exception.TransactionNotFoundException;
import com.splitmanager.api.exception.ValidationException;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.LineItem;
import com.splitmanager.api.model.SplitDefinition;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionStatus;
import com.splitmanager.api.model.TransactionType;
import com.splitmanager.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Ledger writes and reads (LLD 3.3). */
@Service
public class TransactionService {

  private final TransactionRepository repository;
  private final SplitCalculationService splitCalculationService;

  public TransactionService(
      TransactionRepository repository, SplitCalculationService splitCalculationService) {
    this.repository = repository;
    this.splitCalculationService = splitCalculationService;
  }

  /** A parsed receipt, held as a draft until the user reviews it and finalizes. */
  public Transaction createFromReceipt(
      String userId,
      String imageS3Key,
      String merchant,
      LocalDate transactionDate,
      List<LineItem> items,
      BigDecimal subtotal,
      BigDecimal tax,
      BigDecimal total) {
    Transaction transaction =
        baseTransaction(userId, TransactionType.SPLIT, merchant, transactionDate, total);
    transaction.setReceiptImageS3Key(imageS3Key);
    transaction.setItems(items == null ? new ArrayList<>() : items);
    transaction.setSubtotal(subtotal);
    transaction.setTax(tax);
    repository.save(transaction);
    return transaction;
  }

  /** A transaction entered by hand, or promoted from a statement candidate. */
  public Transaction create(
      String userId,
      TransactionType type,
      String merchant,
      LocalDate transactionDate,
      BigDecimal total,
      String sourceStatementImportId) {
    Transaction transaction = baseTransaction(userId, type, merchant, transactionDate, total);
    transaction.setSourceStatementImportId(sourceStatementImportId);
    repository.save(transaction);
    return transaction;
  }

  public Transaction get(String userId, String transactionId) {
    Transaction transaction =
        repository.findById(transactionId).orElseThrow(() -> new TransactionNotFoundException(transactionId));
    // Ownership is checked here rather than trusted from the key: a transaction id is
    // guessable, and the id in the path is client-supplied. Reported as not-found rather
    // than forbidden so the endpoint does not confirm that an id exists.
    if (!transaction.getUserId().equals(userId)) {
      throw new TransactionNotFoundException(transactionId);
    }
    return transaction;
  }

  public List<Transaction> list(
      String userId, TransactionStatus status, TransactionType type, int limit) {
    return repository.listByDateDesc(userId, status, type, limit);
  }

  /** Edits to a draft. Finalized transactions change only through status transitions. */
  public Transaction updateDraft(
      String userId,
      String transactionId,
      String merchant,
      LocalDate transactionDate,
      List<LineItem> items,
      BigDecimal subtotal,
      BigDecimal tax,
      BigDecimal tip,
      BigDecimal total,
      String notes) {
    Transaction transaction = get(userId, transactionId);
    if (transaction.getStatus() != TransactionStatus.DRAFT) {
      throw new ValidationException(
          "Only draft transactions can be edited; this one is " + transaction.getStatus() + ".");
    }
    if (merchant != null) transaction.setMerchant(merchant);
    if (transactionDate != null) transaction.setTransactionDate(transactionDate);
    if (items != null) transaction.setItems(items);
    if (subtotal != null) transaction.setSubtotal(subtotal);
    if (tax != null) transaction.setTax(tax);
    if (tip != null) transaction.setTip(tip);
    if (total != null) transaction.setTotal(total);
    if (notes != null) transaction.setNotes(notes);
    transaction.setUpdatedAt(Instant.now());
    repository.save(transaction);
    return transaction;
  }

  /**
   * Computes and stores the split, moving the transaction to OPEN.
   *
   * <p>The split is always recomputed here from the definition; client-supplied share
   * amounts are never trusted. EXACT mode is not an exception — there the amounts <i>are</i>
   * the definition, and they are validated to sum to the total before use.
   *
   * <p>Idempotent: finalizing an already-finalized transaction recomputes rather than
   * erroring, so a retried request is harmless.
   */
  public Transaction finalizeSplit(
      String userId, String transactionId, SplitDefinition definition, BigDecimal tip) {
    Transaction transaction = get(userId, transactionId);

    if (transaction.getType() == TransactionType.REIMBURSEMENT) {
      throw new ValidationException("Reimbursements are not split — the full amount is claimed.");
    }
    if (tip != null) {
      transaction.setTip(tip);
      recomputeTotal(transaction);
    }

    FinalizedSplit split =
        splitCalculationService.compute(definition, transaction.getTotal(), transaction.getItems());

    transaction.setSplitDefinition(definition);
    transaction.setFinalizedSplit(split);
    if (transaction.getStatus() == TransactionStatus.DRAFT) {
      transaction.setStatus(TransactionStatus.OPEN);
    }
    transaction.setUpdatedAt(Instant.now());
    repository.save(transaction);
    return transaction;
  }

  public Transaction updateStatus(String userId, String transactionId, TransactionStatus next) {
    Transaction transaction = get(userId, transactionId);
    TransactionStatus current = transaction.getStatus();
    if (current == next) {
      return transaction;
    }
    if (!current.canTransitionTo(next)) {
      throw new IllegalStatusTransitionException(current, next);
    }
    // DRAFT -> OPEN is reachable only through finalizeSplit: an unsplit transaction has
    // no amounts owed, so letting it go straight to OPEN would put a hole in the balances.
    if (current == TransactionStatus.DRAFT
        && transaction.getType() == TransactionType.SPLIT
        && transaction.getFinalizedSplit() == null) {
      throw new ValidationException("Finalize the split before marking this transaction open.");
    }
    transaction.setStatus(next);
    transaction.setUpdatedAt(Instant.now());
    repository.save(transaction);
    return transaction;
  }

  public void delete(String userId, String transactionId) {
    get(userId, transactionId);
    repository.delete(transactionId);
  }

  private Transaction baseTransaction(
      String userId,
      TransactionType type,
      String merchant,
      LocalDate transactionDate,
      BigDecimal total) {
    if (total == null || total.signum() <= 0) {
      throw new ValidationException("A transaction total greater than zero is required.");
    }
    Transaction transaction = new Transaction();
    transaction.setTransactionId(UUID.randomUUID().toString());
    transaction.setUserId(userId);
    transaction.setType(type);
    transaction.setStatus(TransactionStatus.DRAFT);
    transaction.setMerchant(merchant);
    transaction.setTransactionDate(transactionDate == null ? LocalDate.now() : transactionDate);
    transaction.setTotal(total);
    transaction.setItems(new ArrayList<>());
    transaction.setCreatedAt(Instant.now());
    transaction.setUpdatedAt(Instant.now());
    return transaction;
  }

  /** Keeps the total consistent when a tip is added after parsing. */
  private void recomputeTotal(Transaction transaction) {
    if (transaction.getSubtotal() == null) {
      return;
    }
    BigDecimal total = transaction.getSubtotal();
    if (transaction.getTax() != null) total = total.add(transaction.getTax());
    if (transaction.getTip() != null) total = total.add(transaction.getTip());
    transaction.setTotal(total);
  }
}

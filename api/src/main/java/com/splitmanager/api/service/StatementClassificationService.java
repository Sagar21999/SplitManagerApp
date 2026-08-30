package com.splitmanager.api.service;

import com.splitmanager.api.model.CandidateClassification;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionStatus;
import com.splitmanager.api.model.TransactionType;
import com.splitmanager.api.parser.RawStatementRow;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Guesses what a statement row is (LLD 6.3). First rule to match wins.
 *
 * <p>Deliberately conservative. A list where half the rows are wrongly flagged stops
 * being read, and an unflagged row is only a missed convenience — the user can still
 * confirm it by hand. So precision is preferred to recall throughout.
 */
@Service
public class StatementClassificationService {

  /** Travel a commuter is likely to claim from an employer. */
  private static final Pattern REIMBURSEMENT_KEYWORDS =
      Pattern.compile(
          "\\b(uber|lyft|transit|metro|mta|amtrak|njt|path|septa|caltrain|via rail|"
              + "taxi|cab|parking|toll|ez ?pass|airlines|airways|delta air|amtrak)\\b");

  /** Merchant categories that usually come with company. */
  private static final Pattern SPLIT_KEYWORDS =
      Pattern.compile(
          "\\b(restaurant|cafe|coffee|bar|grill|kitchen|pizza|sushi|taco|brewery|brewing|"
              + "tavern|bistro|diner|pub|eatery|market|grocery|grocer|supermarket|"
              + "whole foods|trader joe|safeway|kroger|costco|doordash|grubhub|seamless|"
              + "ubereats|uber eats|instacart|resy|opentable)\\b");

  /** Below this, a remembered merchant name is too generic to match on containment. */
  private static final int MIN_HISTORY_MATCH_LENGTH = 4;

  private final BigDecimal splitAmountThreshold;

  public StatementClassificationService(
      @Value("${split-manager.classification.split-amount-threshold:40.00}") BigDecimal splitAmountThreshold) {
    this.splitAmountThreshold = splitAmountThreshold;
  }

  /**
   * Builds the merchant-history lookup once per import rather than per row.
   *
   * <p>History is the strongest signal available and the only one that improves with use:
   * once a merchant has been confirmed as a reimbursement, every later charge from it is
   * classified correctly regardless of what the keyword lists say.
   */
  public Map<String, TransactionType> buildHistory(List<Transaction> transactions) {
    Map<String, TransactionType> history = new HashMap<>();
    for (Transaction transaction : transactions) {
      if (transaction.getStatus() == TransactionStatus.DRAFT) {
        // A draft is an unreviewed guess, quite possibly one this very import created.
        // Learning from it would let a misclassification reinforce itself.
        continue;
      }
      String merchant = Transaction.normalizeMerchant(transaction.getMerchant());
      if (!merchant.isBlank()) {
        // The list arrives newest-first, so the first entry seen for a merchant is the
        // most recent decision the user made about it.
        history.putIfAbsent(merchant, transaction.getType());
      }
    }
    return history;
  }

  public Classification classify(RawStatementRow row, Map<String, TransactionType> history) {
    String merchant = Transaction.normalizeMerchant(row.description());

    TransactionType known = lookupHistory(merchant, history);
    if (known != null) {
      return new Classification(
          known == TransactionType.REIMBURSEMENT
              ? CandidateClassification.LIKELY_REIMBURSEMENT
              : CandidateClassification.LIKELY_SPLIT,
          new BigDecimal("0.95"));
    }

    if (REIMBURSEMENT_KEYWORDS.matcher(merchant).find()) {
      return new Classification(CandidateClassification.LIKELY_REIMBURSEMENT, new BigDecimal("0.80"));
    }

    if (SPLIT_KEYWORDS.matcher(merchant).find()
        || row.amount().compareTo(splitAmountThreshold) > 0) {
      return new Classification(CandidateClassification.LIKELY_SPLIT, new BigDecimal("0.60"));
    }

    return new Classification(CandidateClassification.UNLIKELY, BigDecimal.ZERO);
  }

  /**
   * Exact match first, then containment.
   *
   * <p>Containment is needed because a statement descriptor is a known merchant plus
   * noise — "uber eats 8xkz2", "blue bottle coffee 0412 new york" — so equality alone
   * would learn almost nothing. The longest matching entry wins, so a more specific past
   * decision beats a vaguer one, and short entries are ignored: a two-letter name would
   * otherwise match half the statement.
   */
  private static TransactionType lookupHistory(String merchant, Map<String, TransactionType> history) {
    TransactionType exact = history.get(merchant);
    if (exact != null) {
      return exact;
    }

    String best = null;
    for (String known : history.keySet()) {
      if (known.length() < MIN_HISTORY_MATCH_LENGTH || !merchant.contains(known)) {
        continue;
      }
      if (best == null || known.length() > best.length()) {
        best = known;
      }
    }
    return best == null ? null : history.get(best);
  }

  public record Classification(CandidateClassification classification, BigDecimal confidence) {}
}

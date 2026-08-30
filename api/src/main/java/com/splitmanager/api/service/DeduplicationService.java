package com.splitmanager.api.service;

import com.splitmanager.api.model.DuplicateMatch;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Finds transactions that may already record the same charge (BRD FR19, LLD 7).
 *
 * <p>Matches are warnings. Nothing is merged, hidden, or auto-linked: a genuinely
 * repeated charge — the same coffee shop, same amount, the next morning — is a real
 * second transaction, and silently swallowing it is a worse failure than showing a
 * warning the user can dismiss.
 */
@Service
public class DeduplicationService {

  /**
   * A card statement posts a charge days after the purchase, so the receipt date and the
   * statement date rarely agree. Three days each way covers a weekend plus settlement.
   */
  private static final int DATE_WINDOW_DAYS = 3;

  /** Above this, two descriptors are treated as the same merchant. */
  private static final double STRONG_MATCH_THRESHOLD = 0.85;

  private final TransactionRepository transactionRepository;

  public DeduplicationService(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  public List<DuplicateMatch> findMatches(
      String userId, String merchant, LocalDate date, BigDecimal amount) {
    return findMatches(userId, merchant, date, amount, null);
  }

  /**
   * @param excludeTransactionId a transaction to leave out — used when checking a
   *     just-created receipt draft, which would otherwise match itself
   */
  public List<DuplicateMatch> findMatches(
      String userId, String merchant, LocalDate date, BigDecimal amount, String excludeTransactionId) {
    if (date == null || amount == null) {
      return List.of();
    }

    String normalized = Transaction.normalizeMerchant(merchant);
    List<DuplicateMatch> matches = new ArrayList<>();

    for (int offset = -DATE_WINDOW_DAYS; offset <= DATE_WINDOW_DAYS; offset++) {
      for (Transaction candidate :
          transactionRepository.findByAmountAndDate(userId, amount, date.plusDays(offset))) {
        if (candidate.getTransactionId().equals(excludeTransactionId)) {
          continue;
        }
        double score = similarity(normalized, Transaction.normalizeMerchant(candidate.getMerchant()));
        boolean strong = score >= STRONG_MATCH_THRESHOLD;
        matches.add(
            new DuplicateMatch(
                candidate.getTransactionId(),
                strong ? DuplicateMatch.MERCHANT_DATE_AMOUNT : DuplicateMatch.DATE_AMOUNT,
                // A date+amount match on its own is weak evidence, but the same amount on
                // the same day is still worth a glance, so it is reported at a flat 0.5
                // rather than at its (meaninglessly low) merchant similarity.
                BigDecimal.valueOf(strong ? score : 0.5).setScale(2, RoundingMode.HALF_UP),
                candidate.getMerchant(),
                candidate.getTransactionDate()));
      }
    }

    matches.sort(Comparator.comparing(DuplicateMatch::getScore).reversed());
    return matches;
  }

  /**
   * Levenshtein distance expressed as a 0..1 ratio over the longer string.
   *
   * <p>Statement descriptors are not receipt vendor names — "SQ *BLUE BOTTLE COFF" against
   * "Blue Bottle Coffee" — so exact comparison would miss most true duplicates even after
   * normalisation strips the processor prefix.
   */
  static double similarity(String left, String right) {
    if (left.isEmpty() && right.isEmpty()) {
      return 1.0;
    }
    if (left.isEmpty() || right.isEmpty()) {
      return 0.0;
    }
    if (left.equals(right)) {
      return 1.0;
    }
    int longest = Math.max(left.length(), right.length());
    return 1.0 - ((double) levenshtein(left, right) / longest);
  }

  /** Two-row variant: the full matrix is never needed and these strings can be long. */
  private static int levenshtein(String left, String right) {
    int[] previous = new int[right.length() + 1];
    int[] current = new int[right.length() + 1];

    for (int j = 0; j <= right.length(); j++) {
      previous[j] = j;
    }

    for (int i = 1; i <= left.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= right.length(); j++) {
        int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[right.length()];
  }
}

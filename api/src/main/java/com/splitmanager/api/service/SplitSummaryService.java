package com.splitmanager.api.service;

import com.splitmanager.api.dto.SplitSummaryDto;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.Participants;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Human-readable summaries.
 *
 * <p>Still exists in v2 even though Split Manager is now the system of record: the user
 * often re-enters a transaction into Splitwise by hand so the other party sees it in an
 * app they already have (BRD "Relationship to Splitwise"). That handoff is manual and
 * non-blocking, so this text is a convenience rather than a step in the flow.
 *
 * <p>Pure — takes an already-computed split and formats it. No recomputation.
 */
@Service
public class SplitSummaryService {

  public SplitSummaryDto generateSummary(Transaction transaction, Map<String, String> personNames) {
    FinalizedSplit split = transaction.getFinalizedSplit();
    if (split == null) {
      return new SplitSummaryDto(Map.of(), "");
    }

    Map<String, BigDecimal> owed = new LinkedHashMap<>(split.getParticipantShares());
    String payerLabel = label(split.getPayerId(), personNames);

    StringBuilder text = new StringBuilder();
    text.append(transaction.getMerchant() == null ? "Expense" : transaction.getMerchant());
    text.append(" — ").append(money(transaction.getTotal()));
    text.append(" on ").append(transaction.getTransactionDate()).append('\n');
    text.append(payerLabel).append(" paid").append('\n');

    for (Map.Entry<String, BigDecimal> entry : owed.entrySet()) {
      if (entry.getKey().equals(split.getPayerId())) {
        // The payer's own share is theirs already — listing it as owed reads as a debt
        // to themselves and is the single most confusing thing to paste into Splitwise.
        continue;
      }
      text.append("  ")
          .append(label(entry.getKey(), personNames))
          .append(" owes ")
          .append(money(entry.getValue()))
          .append('\n');
    }

    return new SplitSummaryDto(owed, text.toString().stripTrailing());
  }

  /** BRD FR23: a claim-ready block for work expenses. */
  public String generateReimbursementSummary(List<Transaction> reimbursements) {
    StringBuilder text = new StringBuilder("Reimbursement claim\n");
    BigDecimal total = BigDecimal.ZERO;
    for (Transaction t : reimbursements) {
      if (t.getType() != TransactionType.REIMBURSEMENT) {
        continue;
      }
      text.append("  ")
          .append(t.getTransactionDate())
          .append("  ")
          .append(t.getMerchant() == null ? "(no merchant)" : t.getMerchant())
          .append("  ")
          .append(money(t.getTotal()))
          .append('\n');
      total = total.add(t.getTotal());
    }
    text.append("Total: ").append(money(total));
    return text.toString();
  }

  private String label(String participantId, Map<String, String> personNames) {
    if (Participants.SELF.equals(participantId)) {
      return "You";
    }
    return personNames.getOrDefault(participantId, participantId);
  }

  private String money(BigDecimal amount) {
    return amount == null ? "-" : "$" + amount.toPlainString();
  }
}

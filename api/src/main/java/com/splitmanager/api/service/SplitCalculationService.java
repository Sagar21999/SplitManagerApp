package com.splitmanager.api.service;

import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.ReceiptItem;
import com.splitmanager.api.model.SplitMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SplitCalculationService {

  // Scale used for unrounded intermediate per-item-share division, per LLD §5.
  private static final int INTERMEDIATE_SCALE = 10;

  public FinalizedSplit computeEqualSplit(BigDecimal total, List<String> participantIds) {
    int n = participantIds.size();
    BigDecimal baseShare = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);

    Map<String, BigDecimal> shares = new LinkedHashMap<>();
    for (String id : participantIds) {
      shares.put(id, baseShare);
    }

    BigDecimal distributed = baseShare.multiply(BigDecimal.valueOf(n));
    int remainderCents =
        total.subtract(distributed).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();

    for (int i = 0; i < remainderCents; i++) {
      String id = participantIds.get(i);
      shares.put(id, shares.get(id).add(new BigDecimal("0.01")));
    }

    return new FinalizedSplit(SplitMode.EQUAL, shares, null);
  }

  public FinalizedSplit computeItemSplit(
      List<ReceiptItem> items,
      Map<String, List<String>> itemAssignments,
      BigDecimal tax,
      BigDecimal tip,
      String payerId) {
    BigDecimal subtotal = items.stream().map(ReceiptItem::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<String, BigDecimal> participantSubtotals = subtotalsByParticipant(items, itemAssignments);

    Map<String, BigDecimal> owedShare = new LinkedHashMap<>();
    for (Map.Entry<String, BigDecimal> entry : participantSubtotals.entrySet()) {
      BigDecimal subtotalP = entry.getValue();
      BigDecimal proportion = subtotalP.divide(subtotal, INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
      BigDecimal taxShare = tax.multiply(proportion);
      BigDecimal tipShare = tip.multiply(proportion);
      owedShare.put(entry.getKey(), subtotalP.add(taxShare).add(tipShare).setScale(2, RoundingMode.HALF_UP));
    }

    BigDecimal total = subtotal.add(tax).add(tip);
    applyRoundingRemainder(owedShare, total, payerId);

    return new FinalizedSplit(SplitMode.BY_ITEM, owedShare, payerId);
  }

  private Map<String, BigDecimal> subtotalsByParticipant(
      List<ReceiptItem> items, Map<String, List<String>> assignments) {
    Map<String, BigDecimal> subtotals = new LinkedHashMap<>();
    for (ReceiptItem item : items) {
      List<String> sharers = assignments.get(item.getId());
      BigDecimal perPersonShare =
          item.getPrice().divide(BigDecimal.valueOf(sharers.size()), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
      for (String participantId : sharers) {
        subtotals.merge(participantId, perPersonShare, BigDecimal::add);
      }
    }
    return subtotals;
  }

  private void applyRoundingRemainder(Map<String, BigDecimal> shares, BigDecimal target, String payerId) {
    BigDecimal totalOwed = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remainder = target.subtract(totalOwed);
    shares.merge(payerId, remainder, BigDecimal::add);
  }
}

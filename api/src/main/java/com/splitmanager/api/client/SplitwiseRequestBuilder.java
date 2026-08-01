package com.splitmanager.api.client;

import com.splitmanager.api.dto.SplitwiseExpenseRequestDto;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.ReceiptSession;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure, side-effect-free request construction — used by both the real submit-expense
 * flow and the dry-run endpoint, and unit-testable with no HTTP involved at all.
 */
@Component
public class SplitwiseRequestBuilder {

  public SplitwiseExpenseRequestDto build(ReceiptSession session, FinalizedSplit split) {
    Map<String, BigDecimal> owedShare = new HashMap<>(split.getParticipantShares());
    Map<String, BigDecimal> paidShare = new HashMap<>();
    for (String participantId : owedShare.keySet()) {
      paidShare.put(participantId, BigDecimal.ZERO);
    }
    BigDecimal cost = owedShare.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    paidShare.put(split.getPayerId(), cost);

    return new SplitwiseExpenseRequestDto(
        cost,
        session.getMerchant(),
        paidShare,
        owedShare,
        session.getReceiptImageS3Key());
  }
}

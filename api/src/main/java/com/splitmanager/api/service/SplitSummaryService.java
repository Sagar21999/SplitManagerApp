package com.splitmanager.api.service;

import com.splitmanager.api.dto.SplitSummaryDto;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.ReceiptSession;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Builds a human-readable split summary for the user to manually copy into Splitwise
 * (or any other app) — replaces the old direct-to-Splitwise-API submission.
 */
@Service
public class SplitSummaryService {

  public SplitSummaryDto generateSummary(ReceiptSession session, FinalizedSplit split) {
    Map<String, BigDecimal> amountOwed = new TreeMap<>(split.getParticipantShares());

    StringBuilder text = new StringBuilder();
    text.append(session.getMerchant()).append(" — $").append(session.getTotal()).append('\n');
    text.append(split.getPayerId()).append(" paid\n");
    for (Map.Entry<String, BigDecimal> entry : amountOwed.entrySet()) {
      text.append(entry.getKey()).append(": $").append(entry.getValue()).append('\n');
    }

    return new SplitSummaryDto(amountOwed, text.toString().stripTrailing());
  }
}

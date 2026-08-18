package com.splitmanager.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitmanager.api.dto.SplitSummaryDto;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.ReceiptSession;
import com.splitmanager.api.model.SessionStatus;
import com.splitmanager.api.model.SplitMode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class SplitSummaryServiceTest {

  private final SplitSummaryService service = new SplitSummaryService();

  private ReceiptSession session(String merchant, BigDecimal total) {
    ReceiptSession session = new ReceiptSession();
    session.setMerchant(merchant);
    session.setTotal(total);
    return session;
  }

  @Test
  void breakdownMatchesTheSubmittedShares() {
    Map<String, BigDecimal> shares = new TreeMap<>();
    shares.put("alice", new BigDecimal("9.00"));
    shares.put("bob", new BigDecimal("9.00"));
    FinalizedSplit split = new FinalizedSplit(SplitMode.EQUAL, shares, "alice");

    SplitSummaryDto summary = service.generateSummary(session("Diner", new BigDecimal("18.00")), split);

    assertEquals(shares, summary.getAmountOwedByParticipant());
  }

  @Test
  void shareTextIncludesMerchantTotalPayerAndEachParticipant() {
    Map<String, BigDecimal> shares = new TreeMap<>();
    shares.put("alice", new BigDecimal("9.00"));
    shares.put("bob", new BigDecimal("9.00"));
    FinalizedSplit split = new FinalizedSplit(SplitMode.EQUAL, shares, "alice");

    SplitSummaryDto summary = service.generateSummary(session("Diner", new BigDecimal("18.00")), split);

    String text = summary.getShareText();
    assertTrue(text.contains("Diner"));
    assertTrue(text.contains("18.00"));
    assertTrue(text.contains("alice paid"));
    assertTrue(text.contains("alice: $9.00"));
    assertTrue(text.contains("bob: $9.00"));
  }

  @Test
  void shareTextListsParticipantsInSortedOrder() {
    Map<String, BigDecimal> shares = new TreeMap<>();
    shares.put("carol", new BigDecimal("5.00"));
    shares.put("alice", new BigDecimal("5.00"));
    shares.put("bob", new BigDecimal("5.00"));
    FinalizedSplit split = new FinalizedSplit(SplitMode.BY_ITEM, shares, "carol");

    SplitSummaryDto summary = service.generateSummary(session("Cafe", new BigDecimal("15.00")), split);

    String text = summary.getShareText();
    int aliceIndex = text.indexOf("alice");
    int bobIndex = text.indexOf("bob:");
    int carolIndex = text.indexOf("carol:");
    assertTrue(aliceIndex < bobIndex && bobIndex < carolIndex, text);
  }

  @Test
  void statusUnusedFieldDoesNotAffectSummary() {
    ReceiptSession session = session("Bar", new BigDecimal("10.00"));
    session.setStatus(SessionStatus.PARSED);
    Map<String, BigDecimal> shares = Map.of("alice", new BigDecimal("10.00"));
    FinalizedSplit split = new FinalizedSplit(SplitMode.EQUAL, shares, "alice");

    SplitSummaryDto summary = service.generateSummary(session, split);

    assertEquals(new BigDecimal("10.00"), summary.getAmountOwedByParticipant().get("alice"));
  }
}

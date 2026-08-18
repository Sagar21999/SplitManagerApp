package com.splitmanager.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.ReceiptItem;
import com.splitmanager.api.model.SplitMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplitCalculationServiceTest {

  private final SplitCalculationService service = new SplitCalculationService();

  private static BigDecimal sum(Map<String, BigDecimal> shares) {
    return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Test
  void equalSplitSingleParticipantGetsWholeTotal() {
    FinalizedSplit split = service.computeEqualSplit(new BigDecimal("18.00"), List.of("alice"));

    assertEquals(SplitMode.EQUAL, split.getMode());
    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("18.00")));
  }

  @Test
  void equalSplitDividesEvenlyWhenExact() {
    FinalizedSplit split = service.computeEqualSplit(new BigDecimal("20.00"), List.of("alice", "bob"));

    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("10.00")));
    assertEquals(0, split.getParticipantShares().get("bob").compareTo(new BigDecimal("10.00")));
    assertEquals(0, sum(split.getParticipantShares()).compareTo(new BigDecimal("20.00")));
  }

  @Test
  void equalSplitDistributesRoundingRemainderOneCentAtATime() {
    // 10.00 / 3 = 3.33 base share each, with 1 cent left over (rounding remainder).
    FinalizedSplit split =
        service.computeEqualSplit(new BigDecimal("10.00"), List.of("alice", "bob", "carol"));

    Map<String, BigDecimal> shares = split.getParticipantShares();
    assertEquals(0, shares.get("alice").compareTo(new BigDecimal("3.34")));
    assertEquals(0, shares.get("bob").compareTo(new BigDecimal("3.33")));
    assertEquals(0, shares.get("carol").compareTo(new BigDecimal("3.33")));
    assertEquals(0, sum(shares).compareTo(new BigDecimal("10.00")));
  }

  @Test
  void itemSplitSingleItemSharedByAll() {
    List<ReceiptItem> items = List.of(new ReceiptItem("i1", "Pizza", new BigDecimal("20.00")));
    Map<String, List<String>> assignments = Map.of("i1", List.of("alice", "bob"));

    FinalizedSplit split =
        service.computeItemSplit(items, assignments, BigDecimal.ZERO, BigDecimal.ZERO, "alice");

    assertEquals(SplitMode.BY_ITEM, split.getMode());
    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("10.00")));
    assertEquals(0, split.getParticipantShares().get("bob").compareTo(new BigDecimal("10.00")));
  }

  @Test
  void itemSplitUnevenSharersOnlyChargesAssignedParticipants() {
    List<ReceiptItem> items =
        List.of(
            new ReceiptItem("i1", "Burger", new BigDecimal("12.00")), // alice only
            new ReceiptItem("i2", "Nachos", new BigDecimal("9.00"))); // shared by all 3
    Map<String, List<String>> assignments =
        Map.of("i1", List.of("alice"), "i2", List.of("alice", "bob", "carol"));

    FinalizedSplit split =
        service.computeItemSplit(items, assignments, BigDecimal.ZERO, BigDecimal.ZERO, "alice");

    Map<String, BigDecimal> shares = split.getParticipantShares();
    // alice: 12.00 + 3.00 = 15.00, bob/carol: 3.00 each
    assertEquals(0, shares.get("alice").compareTo(new BigDecimal("15.00")));
    assertEquals(0, shares.get("bob").compareTo(new BigDecimal("3.00")));
    assertEquals(0, shares.get("carol").compareTo(new BigDecimal("3.00")));
    assertEquals(0, sum(shares).compareTo(new BigDecimal("21.00")));
  }

  @Test
  void itemSplitWithZeroTipDoesNotAddAnything() {
    List<ReceiptItem> items = List.of(new ReceiptItem("i1", "Coffee", new BigDecimal("5.00")));
    Map<String, List<String>> assignments = Map.of("i1", List.of("alice"));

    FinalizedSplit split =
        service.computeItemSplit(items, assignments, new BigDecimal("0.50"), BigDecimal.ZERO, "alice");

    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("5.50")));
  }

  @Test
  void itemSplitAbsorbsRoundingRemainderIntoPayerShare() {
    // Item split three ways produces a repeating decimal per-person share; the payer
    // should absorb whatever rounding drift is left so the shares sum exactly to total.
    List<ReceiptItem> items = List.of(new ReceiptItem("i1", "Appetizer", new BigDecimal("10.00")));
    Map<String, List<String>> assignments = Map.of("i1", List.of("alice", "bob", "carol"));
    BigDecimal tax = new BigDecimal("1.00");
    BigDecimal tip = new BigDecimal("2.00");
    BigDecimal total = new BigDecimal("10.00").add(tax).add(tip);

    FinalizedSplit split = service.computeItemSplit(items, assignments, tax, tip, "alice");

    assertEquals(0, sum(split.getParticipantShares()).compareTo(total));
    assertTrue(split.getParticipantShares().get("alice").compareTo(BigDecimal.ZERO) > 0);
  }
}

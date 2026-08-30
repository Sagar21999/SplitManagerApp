package com.splitmanager.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitmanager.api.exception.ValidationException;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.LineItem;
import com.splitmanager.api.model.SplitDefinition;
import com.splitmanager.api.model.SplitMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The correctness net for the ledger's money math.
 *
 * <p>v1's rounding-remainder cases are carried over deliberately: they were the reason
 * the v2 refactor to a single weight pipeline could be made with any confidence, and they
 * still pass unchanged against the new implementation.
 */
class SplitCalculationServiceTest {

  private final SplitCalculationService service = new SplitCalculationService();

  private static BigDecimal sum(Map<String, BigDecimal> shares) {
    return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static SplitDefinition def(SplitMode mode, String payerId, List<String> participants) {
    SplitDefinition d = new SplitDefinition();
    d.setMode(mode);
    d.setPayerId(payerId);
    d.setParticipantIds(participants);
    return d;
  }

  // ---------- EQUAL ----------

  @Test
  void equalSplitSingleParticipantGetsWholeTotal() {
    FinalizedSplit split =
        service.compute(def(SplitMode.EQUAL, "alice", List.of("alice")), new BigDecimal("18.00"), List.of());

    assertEquals(SplitMode.EQUAL, split.getMode());
    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("18.00")));
  }

  @Test
  void equalSplitDividesEvenlyWhenExact() {
    FinalizedSplit split =
        service.compute(
            def(SplitMode.EQUAL, "alice", List.of("alice", "bob")), new BigDecimal("20.00"), List.of());

    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("10.00")));
    assertEquals(0, split.getParticipantShares().get("bob").compareTo(new BigDecimal("10.00")));
    assertEquals(0, sum(split.getParticipantShares()).compareTo(new BigDecimal("20.00")));
  }

  @Test
  void equalSplitOfIndivisibleTotalStillSumsExactly() {
    // 10.00 / 3 = 3.3333... - the classic case where naive rounding loses or gains a cent.
    FinalizedSplit split =
        service.compute(
            def(SplitMode.EQUAL, "alice", List.of("alice", "bob", "carol")),
            new BigDecimal("10.00"),
            List.of());

    assertEquals(0, sum(split.getParticipantShares()).compareTo(new BigDecimal("10.00")));
  }

  @Test
  void payerAbsorbsTheRoundingRemainder() {
    FinalizedSplit split =
        service.compute(
            def(SplitMode.EQUAL, "bob", List.of("alice", "bob", "carol")),
            new BigDecimal("10.00"),
            List.of());

    // 3.33 each leaves a cent; the payer takes it rather than an arbitrary participant.
    assertEquals(0, split.getParticipantShares().get("bob").compareTo(new BigDecimal("3.34")));
    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("3.33")));
    assertEquals(0, split.getParticipantShares().get("carol").compareTo(new BigDecimal("3.33")));
  }

  @Test
  void remainderIsSpreadWhenThePayerIsNotAParticipant() {
    // Someone paid but consumed nothing, so there is no payer share to absorb the drift.
    FinalizedSplit split =
        service.compute(
            def(SplitMode.EQUAL, "dave", List.of("alice", "bob", "carol")),
            new BigDecimal("10.00"),
            List.of());

    assertEquals(0, sum(split.getParticipantShares()).compareTo(new BigDecimal("10.00")));
    assertTrue(!split.getParticipantShares().containsKey("dave"));
  }

  // ---------- SHARES ----------

  @Test
  void sharesModeWeightsProportionally() {
    SplitDefinition d = def(SplitMode.SHARES, "alice", List.of("alice", "bob"));
    d.setWeights(Map.of("alice", new BigDecimal("2"), "bob", new BigDecimal("1")));

    FinalizedSplit split = service.compute(d, new BigDecimal("30.00"), List.of());

    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("20.00")));
    assertEquals(0, split.getParticipantShares().get("bob").compareTo(new BigDecimal("10.00")));
  }

  // ---------- PERCENTAGE ----------

  @Test
  void percentageModeSplitsByPercent() {
    SplitDefinition d = def(SplitMode.PERCENTAGE, "alice", List.of("alice", "bob"));
    d.setWeights(Map.of("alice", new BigDecimal("25"), "bob", new BigDecimal("75")));

    FinalizedSplit split = service.compute(d, new BigDecimal("40.00"), List.of());

    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("10.00")));
    assertEquals(0, split.getParticipantShares().get("bob").compareTo(new BigDecimal("30.00")));
  }

  @Test
  void percentageModeRejectsWeightsNotSummingTo100() {
    SplitDefinition d = def(SplitMode.PERCENTAGE, "alice", List.of("alice", "bob"));
    d.setWeights(Map.of("alice", new BigDecimal("30"), "bob", new BigDecimal("30")));

    assertThrows(ValidationException.class, () -> service.compute(d, new BigDecimal("40.00"), List.of()));
  }

  // ---------- EXACT ----------

  @Test
  void exactModeUsesSuppliedAmounts() {
    SplitDefinition d = def(SplitMode.EXACT, "alice", List.of("alice", "bob"));
    d.setWeights(Map.of("alice", new BigDecimal("12.50"), "bob", new BigDecimal("7.50")));

    FinalizedSplit split = service.compute(d, new BigDecimal("20.00"), List.of());

    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("12.50")));
    assertEquals(0, split.getParticipantShares().get("bob").compareTo(new BigDecimal("7.50")));
  }

  @Test
  void exactModeRejectsAmountsNotSummingToTotal() {
    SplitDefinition d = def(SplitMode.EXACT, "alice", List.of("alice", "bob"));
    d.setWeights(Map.of("alice", new BigDecimal("12.50"), "bob", new BigDecimal("5.00")));

    assertThrows(ValidationException.class, () -> service.compute(d, new BigDecimal("20.00"), List.of()));
  }

  // ---------- BY_ITEM ----------

  @Test
  void byItemProratesTaxAndTipBySubtotalShare() {
    List<LineItem> items =
        List.of(
            new LineItem("i1", "Steak", new BigDecimal("22.00")),
            new LineItem("i2", "Salad", new BigDecimal("4.00")));

    SplitDefinition d = def(SplitMode.BY_ITEM, "alice", List.of("alice", "bob"));
    d.setItemAssignments(Map.of("i1", List.of("alice"), "i2", List.of("bob")));

    // Subtotal 26.00 plus 4.00 of tax/tip = 30.00. Alice ate 22/26 of the food, so she
    // carries 22/26 of the extra, not half of it.
    FinalizedSplit split = service.compute(d, new BigDecimal("30.00"), items);

    assertEquals(0, sum(split.getParticipantShares()).compareTo(new BigDecimal("30.00")));
    assertTrue(
        split.getParticipantShares().get("alice").compareTo(split.getParticipantShares().get("bob")) > 0);
    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("25.38")));
  }

  @Test
  void byItemSharedItemSplitsBetweenSharers() {
    List<LineItem> items = List.of(new LineItem("i1", "Nachos", new BigDecimal("12.00")));

    SplitDefinition d = def(SplitMode.BY_ITEM, "alice", List.of("alice", "bob"));
    d.setItemAssignments(Map.of("i1", List.of("alice", "bob")));

    FinalizedSplit split = service.compute(d, new BigDecimal("12.00"), items);

    assertEquals(0, split.getParticipantShares().get("alice").compareTo(new BigDecimal("6.00")));
    assertEquals(0, split.getParticipantShares().get("bob").compareTo(new BigDecimal("6.00")));
  }

  @Test
  void byItemRejectsAnUnassignedItem() {
    List<LineItem> items =
        List.of(
            new LineItem("i1", "Steak", new BigDecimal("22.00")),
            new LineItem("i2", "Salad", new BigDecimal("4.00")));

    SplitDefinition d = def(SplitMode.BY_ITEM, "alice", List.of("alice", "bob"));
    d.setItemAssignments(Map.of("i1", List.of("alice")));

    assertThrows(ValidationException.class, () -> service.compute(d, new BigDecimal("26.00"), items));
  }

  // ---------- the cross-mode invariant ----------

  @Test
  void everyModeSumsExactlyToTotal() {
    BigDecimal total = new BigDecimal("100.03");
    List<String> people = List.of("alice", "bob", "carol");
    List<LineItem> items =
        List.of(
            new LineItem("i1", "A", new BigDecimal("33.34")),
            new LineItem("i2", "B", new BigDecimal("33.34")),
            new LineItem("i3", "C", new BigDecimal("33.35")));

    SplitDefinition equal = def(SplitMode.EQUAL, "alice", people);

    SplitDefinition shares = def(SplitMode.SHARES, "alice", people);
    shares.setWeights(
        Map.of("alice", new BigDecimal("3"), "bob", new BigDecimal("2"), "carol", new BigDecimal("1")));

    SplitDefinition percentage = def(SplitMode.PERCENTAGE, "alice", people);
    percentage.setWeights(
        Map.of("alice", new BigDecimal("33"), "bob", new BigDecimal("33"), "carol", new BigDecimal("34")));

    SplitDefinition exact = def(SplitMode.EXACT, "alice", people);
    exact.setWeights(
        Map.of(
            "alice", new BigDecimal("33.34"),
            "bob", new BigDecimal("33.34"),
            "carol", new BigDecimal("33.35")));

    SplitDefinition byItem = def(SplitMode.BY_ITEM, "alice", people);
    byItem.setItemAssignments(
        Map.of("i1", List.of("alice"), "i2", List.of("bob"), "i3", List.of("carol")));

    for (SplitDefinition d : List.of(equal, shares, percentage, exact, byItem)) {
      FinalizedSplit split = service.compute(d, total, items);
      assertEquals(
          0,
          sum(split.getParticipantShares()).compareTo(total),
          "shares must sum to the total exactly for mode " + d.getMode());
    }
  }

  // ---------- validation ----------

  @Test
  void rejectsEmptyParticipantList() {
    assertThrows(
        ValidationException.class,
        () -> service.compute(def(SplitMode.EQUAL, "alice", List.of()), new BigDecimal("10.00"), List.of()));
  }

  @Test
  void rejectsNonPositiveTotal() {
    assertThrows(
        ValidationException.class,
        () ->
            service.compute(
                def(SplitMode.EQUAL, "alice", List.of("alice")), new BigDecimal("0.00"), List.of()));
  }
}

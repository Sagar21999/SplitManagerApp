package com.splitmanager.api.service;

import com.splitmanager.api.exception.ValidationException;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.LineItem;
import com.splitmanager.api.model.SplitDefinition;
import com.splitmanager.api.model.SplitMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Computes per-person amounts for all five split modes (LLD 5).
 *
 * <p>v1 had two unrelated methods, {@code computeEqualSplit} and {@code computeItemSplit}.
 * Both are now cases of one pipeline: <b>resolve a weight per participant, distribute the
 * total in proportion to those weights, then hand the rounding remainder to the payer.</b>
 * Adding SHARES, PERCENTAGE, and EXACT was therefore a change to weight resolution only —
 * the distribution and remainder logic, which is the part that is actually easy to get
 * wrong, is unchanged from v1 and still covered by v1's tests.
 *
 * <p>Invariant guaranteed for every mode: the returned shares sum to {@code total}
 * exactly. SplitSummaryService and BalanceService both rely on it.
 */
@Service
public class SplitCalculationService {

  /** Scale for unrounded intermediate division, per LLD 5. */
  private static final int INTERMEDIATE_SCALE = 10;

  private static final BigDecimal ONE_CENT = new BigDecimal("0.01");
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  public FinalizedSplit compute(SplitDefinition definition, BigDecimal total, List<LineItem> items) {
    validate(definition, total);

    Map<String, BigDecimal> weights = resolveWeights(definition, total, items);
    Map<String, BigDecimal> shares = distribute(total, weights);
    applyRoundingRemainder(shares, total, definition.getPayerId(), definition.getParticipantIds());

    return new FinalizedSplit(
        definition.getMode(), shares, definition.getPayerId(), Instant.now());
  }

  private void validate(SplitDefinition definition, BigDecimal total) {
    if (definition == null || definition.getMode() == null) {
      throw new ValidationException("A split mode is required.");
    }
    List<String> participants = definition.getParticipantIds();
    if (participants == null || participants.isEmpty()) {
      throw new ValidationException("At least one participant is required.");
    }
    if (total == null || total.signum() <= 0) {
      throw new ValidationException("Transaction total must be greater than zero.");
    }

    switch (definition.getMode()) {
      case SHARES, PERCENTAGE, EXACT -> {
        Map<String, BigDecimal> weights = definition.getWeights();
        if (weights == null || weights.isEmpty()) {
          throw new ValidationException(
              definition.getMode() + " requires a weight for each participant.");
        }
        for (String id : participants) {
          BigDecimal w = weights.get(id);
          if (w == null) {
            throw new ValidationException("Missing " + definition.getMode() + " value for " + id);
          }
          if (w.signum() < 0) {
            throw new ValidationException("Values cannot be negative (" + id + ").");
          }
        }
        BigDecimal sum = sum(weights.values());
        if (definition.getMode() == SplitMode.PERCENTAGE
            && sum.compareTo(ONE_HUNDRED) != 0) {
          throw new ValidationException("Percentages must sum to 100, got " + sum + ".");
        }
        if (definition.getMode() == SplitMode.EXACT
            && sum.setScale(2, RoundingMode.HALF_UP).compareTo(total.setScale(2, RoundingMode.HALF_UP))
                != 0) {
          throw new ValidationException("Exact amounts must sum to " + total + ", got " + sum + ".");
        }
        if (sum.signum() <= 0) {
          throw new ValidationException("Weights must sum to more than zero.");
        }
      }
      case BY_ITEM -> {
        if (definition.getItemAssignments() == null || definition.getItemAssignments().isEmpty()) {
          throw new ValidationException("By-item splits require item assignments.");
        }
      }
      case EQUAL -> {
        /* nothing further to validate */
      }
    }
  }

  /**
   * The only part that differs per mode. Weights need no particular unit or scale —
   * {@link #distribute} normalizes by their sum — so percentages, share counts, exact
   * amounts, and per-item subtotals are all interchangeable here.
   */
  private Map<String, BigDecimal> resolveWeights(
      SplitDefinition definition, BigDecimal total, List<LineItem> items) {
    return switch (definition.getMode()) {
      case EQUAL -> {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        definition.getParticipantIds().forEach(id -> weights.put(id, BigDecimal.ONE));
        yield weights;
      }
      case SHARES, PERCENTAGE, EXACT -> {
        // Preserve participant order so remainder distribution is deterministic.
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        definition.getParticipantIds().forEach(id -> weights.put(id, definition.getWeights().get(id)));
        yield weights;
      }
      case BY_ITEM -> subtotalsByParticipant(items, definition);
    };
  }

  /**
   * Per-participant subtotals from item assignments. Tax and tip are not handled here:
   * distributing the full total in proportion to these subtotals prorates them
   * automatically, which is what LLD 5's by-item rule asks for.
   */
  private Map<String, BigDecimal> subtotalsByParticipant(
      List<LineItem> items, SplitDefinition definition) {
    if (items == null || items.isEmpty()) {
      throw new ValidationException("By-item splits require line items.");
    }
    Map<String, List<String>> assignments = definition.getItemAssignments();
    Map<String, BigDecimal> subtotals = new LinkedHashMap<>();
    // Seed every participant so someone assigned nothing still appears, at zero.
    definition.getParticipantIds().forEach(id -> subtotals.put(id, BigDecimal.ZERO));

    for (LineItem item : items) {
      List<String> sharers = assignments.get(item.getId());
      if (sharers == null || sharers.isEmpty()) {
        throw new ValidationException("Item \"" + item.getName() + "\" is not assigned to anyone.");
      }
      BigDecimal perPerson =
          item.getPrice().divide(BigDecimal.valueOf(sharers.size()), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
      for (String participantId : sharers) {
        subtotals.merge(participantId, perPerson, BigDecimal::add);
      }
    }

    if (sum(subtotals.values()).signum() <= 0) {
      throw new ValidationException("Assigned items must total more than zero.");
    }
    return subtotals;
  }

  private Map<String, BigDecimal> distribute(BigDecimal total, Map<String, BigDecimal> weights) {
    BigDecimal totalWeight = sum(weights.values());
    Map<String, BigDecimal> shares = new LinkedHashMap<>();
    for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
      BigDecimal proportion =
          entry.getValue().divide(totalWeight, INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
      shares.put(entry.getKey(), total.multiply(proportion).setScale(2, RoundingMode.HALF_UP));
    }
    return shares;
  }

  /**
   * Forces the shares to sum to the total exactly. Rounding each share independently
   * leaves a drift of a cent or two either way; the payer absorbs it, since they fronted
   * the money and are the only participant for whom a one-cent adjustment is not a
   * surprise. If the payer is not among the participants (they paid but consumed
   * nothing), the drift is spread a cent at a time in participant order instead.
   */
  private void applyRoundingRemainder(
      Map<String, BigDecimal> shares, BigDecimal total, String payerId, List<String> participantIds) {
    BigDecimal remainder = total.subtract(sum(shares.values()));
    if (remainder.signum() == 0) {
      return;
    }

    if (payerId != null && shares.containsKey(payerId)) {
      shares.merge(payerId, remainder, BigDecimal::add);
      return;
    }

    int cents = remainder.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    BigDecimal step = cents > 0 ? ONE_CENT : ONE_CENT.negate();
    List<String> order = new ArrayList<>(participantIds);
    for (int i = 0; i < Math.abs(cents); i++) {
      String id = order.get(i % order.size());
      shares.merge(id, step, BigDecimal::add);
    }
  }

  private static BigDecimal sum(Iterable<BigDecimal> values) {
    BigDecimal acc = BigDecimal.ZERO;
    for (BigDecimal v : values) {
      acc = acc.add(v);
    }
    return acc;
  }
}

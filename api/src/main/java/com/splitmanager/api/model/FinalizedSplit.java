package com.splitmanager.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * The computed result of applying a {@link SplitDefinition} to a transaction total.
 *
 * <p>Invariant, enforced by SplitCalculationService and asserted in its tests: the values
 * of {@code participantShares} sum to the transaction total exactly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class FinalizedSplit {
  private SplitMode mode;
  /** participantId to amount owed. Includes SELF when the owner shared in the expense. */
  private Map<String, BigDecimal> participantShares;
  private String payerId;
  private Instant computedAt;
}

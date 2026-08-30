package com.splitmanager.api.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * The user's split intent - the inputs, not the result. Stored alongside the computed
 * {@link FinalizedSplit} so a transaction can be recomputed or re-edited later without
 * having to reverse-engineer the amounts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class SplitDefinition {
  private SplitMode mode;

  /** personId, or {@link Participants#SELF}. Receives the rounding remainder. */
  private String payerId;

  private List<String> participantIds;

  /** SHARES / PERCENTAGE / EXACT only. Null for EQUAL and BY_ITEM. */
  private Map<String, BigDecimal> weights;

  /** BY_ITEM only: lineItemId to the participantIds sharing it. */
  private Map<String, List<String>> itemAssignments;
}

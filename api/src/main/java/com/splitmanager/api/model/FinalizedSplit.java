package com.splitmanager.api.model;

import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class FinalizedSplit {
  private SplitMode mode;
  private Map<String, BigDecimal> participantShares;
  private String payerId;
}

package com.splitmanager.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplitSummaryDto {
  private Map<String, BigDecimal> amountOwedByParticipant;
  private String shareText;
}

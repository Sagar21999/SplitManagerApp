package com.splitmanager.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The exact multipart/form-data shape sent to Splitwise's create_expense. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplitwiseExpenseRequestDto {
  private BigDecimal cost;
  private String description;
  private Map<String, BigDecimal> paidShare;
  private Map<String, BigDecimal> owedShare;
  private String receiptImageS3Key;
}

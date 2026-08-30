package com.splitmanager.api.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/** One line off a receipt. Replaces v1's ReceiptItem. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class LineItem {
  private String id;
  private String name;
  private BigDecimal price;
}

package com.splitmanager.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ReceiptSession {
  private String sessionId;
  private String userId;
  private SessionStatus status;
  private Instant createdAt;
  private long expiresAt;
  private String receiptImageS3Key;
  private String receiptImageContentType;
  private String merchant;
  private List<ReceiptItem> items;
  private BigDecimal tax;
  private BigDecimal tip;
  private BigDecimal total;
  private FinalizedSplit finalizedSplit;
  private String failureReason;

  @DynamoDbPartitionKey
  public String getSessionId() {
    return sessionId;
  }
}

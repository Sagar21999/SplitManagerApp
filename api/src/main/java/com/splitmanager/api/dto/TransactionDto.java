package com.splitmanager.api.dto;

import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.LineItem;
import com.splitmanager.api.model.SplitDefinition;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionStatus;
import com.splitmanager.api.model.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Wire shape of a transaction. Deliberately omits the internal pk/sk/gsi attributes. */
public record TransactionDto(
    String transactionId,
    TransactionType type,
    TransactionStatus status,
    String merchant,
    LocalDate transactionDate,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal tip,
    BigDecimal total,
    List<LineItem> items,
    SplitDefinition splitDefinition,
    FinalizedSplit finalizedSplit,
    String sourceStatementImportId,
    String notes,
    Instant createdAt,
    Instant updatedAt) {

  public static TransactionDto from(Transaction t) {
    return new TransactionDto(
        t.getTransactionId(),
        t.getType(),
        t.getStatus(),
        t.getMerchant(),
        t.getTransactionDate(),
        t.getSubtotal(),
        t.getTax(),
        t.getTip(),
        t.getTotal(),
        t.getItems(),
        t.getSplitDefinition(),
        t.getFinalizedSplit(),
        t.getSourceStatementImportId(),
        t.getNotes(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }
}

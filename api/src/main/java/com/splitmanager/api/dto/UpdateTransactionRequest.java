package com.splitmanager.api.dto;

import com.splitmanager.api.model.LineItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Every field optional - only non-null values are applied. Drafts only. */
public record UpdateTransactionRequest(
    String merchant,
    LocalDate transactionDate,
    List<LineItem> items,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal tip,
    BigDecimal total,
    String notes) {}

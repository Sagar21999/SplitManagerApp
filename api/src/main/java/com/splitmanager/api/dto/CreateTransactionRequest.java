package com.splitmanager.api.dto;

import com.splitmanager.api.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTransactionRequest(
    TransactionType type,
    String merchant,
    LocalDate transactionDate,
    BigDecimal total,
    String sourceStatementImportId) {}

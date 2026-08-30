package com.splitmanager.api.dto;

import com.splitmanager.api.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Optional corrections applied when a candidate is promoted to a transaction. Every field
 * is nullable — an unedited confirm sends an empty body and the candidate's own values
 * are used.
 */
public record ConfirmCandidateRequest(
    TransactionType type, String merchant, LocalDate date, BigDecimal amount) {}

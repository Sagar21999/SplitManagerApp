package com.splitmanager.api.dto;

/** A transaction plus its rendered summary - what the detail page needs in one call. */
public record TransactionDetailDto(TransactionDto transaction, SplitSummaryDto summary) {}

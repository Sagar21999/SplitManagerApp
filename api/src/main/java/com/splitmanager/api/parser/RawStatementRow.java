package com.splitmanager.api.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One extracted statement line, before classification or dedup.
 *
 * <p>{@code amount} is always positive; direction lives in {@code credit} rather than in
 * the sign, because issuers disagree about which way debits point.
 */
public record RawStatementRow(LocalDate date, String description, BigDecimal amount, boolean credit) {}

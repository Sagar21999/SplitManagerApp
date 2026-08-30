package com.splitmanager.api.parser;

/**
 * Column mapping for one card issuer's CSV export (LLD 6.1).
 *
 * @param debitCreditColumn optional column marking the row as a credit. Issuers do this
 *     two ways and both are accepted: a direction word ("credit", "cr", "payment"), or a
 *     separate credit-amount column that is blank on purchases and populated on refunds.
 *     When absent, direction is read from the sign of the amount instead.
 * @param debitsArePositive whether a purchase appears as a positive number. Issuers are
 *     split roughly evenly on this, and guessing wrong inverts the entire statement.
 */
public record IssuerProfile(
    String id,
    String label,
    String dateColumn,
    String descriptionColumn,
    String amountColumn,
    String debitCreditColumn,
    String dateFormat,
    boolean debitsArePositive) {}

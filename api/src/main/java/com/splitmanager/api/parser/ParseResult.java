package com.splitmanager.api.parser;

import java.util.List;

/**
 * The outcome of parsing a statement file.
 *
 * <p>Carries counts as well as rows because a partial parse is a success, not a failure
 * (LLD 10): 40 rows out of 45 is worth returning, but only if the user is told about the
 * other 5.
 *
 * @param rows debit rows that parsed cleanly
 * @param totalRows every data row the parser saw
 * @param creditRows rows discarded as payments or refunds — expected, not a problem
 * @param droppedRows rows that could not be parsed at all
 */
public record ParseResult(List<RawStatementRow> rows, int totalRows, int creditRows, int droppedRows) {}

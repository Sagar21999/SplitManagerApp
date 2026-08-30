package com.splitmanager.api.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitmanager.api.exception.StatementParseException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CsvStatementParserTest {

  private final CsvStatementParser parser = new CsvStatementParser(new IssuerProfileRegistry());

  private ParseResult parse(String csv, String profile) {
    return parser.parse(csv.getBytes(StandardCharsets.UTF_8), profile);
  }

  @Test
  void parsesKnownIssuerProfile() {
    // Chase writes purchases as negatives, which is the opposite of most issuers - the
    // profile is the only thing that gets this right.
    String csv =
        """
        Transaction Date,Post Date,Description,Category,Type,Amount
        08/12/2026,08/14/2026,BLUE BOTTLE COFFEE,Food & Drink,Sale,-18.40
        08/13/2026,08/15/2026,PAYMENT THANK YOU,,Payment,500.00
        """;

    ParseResult result = parse(csv, "chase");

    assertEquals(2, result.totalRows());
    assertEquals(1, result.creditRows());
    assertEquals(0, result.droppedRows());
    assertEquals(1, result.rows().size());

    RawStatementRow row = result.rows().get(0);
    assertEquals(LocalDate.of(2026, 8, 12), row.date());
    assertEquals("BLUE BOTTLE COFFEE", row.description());
    assertEquals(0, new BigDecimal("18.40").compareTo(row.amount()));
  }

  @Test
  void infersColumnsWithoutAProfile() {
    String csv =
        """
        Date,Description,Amount
        2026-08-12,Trader Joes,54.21
        2026-08-13,REFUND - RETURNED ITEM,-12.00
        """;

    ParseResult result = parse(csv, null);

    assertEquals(1, result.rows().size());
    assertEquals("Trader Joes", result.rows().get(0).description());
    // Without a profile the common convention is assumed, so the negative reads as a
    // credit rather than as a purchase.
    assertEquals(1, result.creditRows());
  }

  @Test
  void readsAmountsOutOfASeparateCreditColumn() {
    String csv =
        """
        Transaction Date,Posted Date,Description,Debit,Credit
        2026-08-12,2026-08-13,SQ *BLUE BOTTLE,18.40,
        2026-08-14,2026-08-15,CREDIT VOUCHER,,25.00
        """;

    ParseResult result = parse(csv, "capital-one");

    assertEquals(1, result.rows().size());
    assertEquals(1, result.creditRows());
    assertEquals(0, new BigDecimal("18.40").compareTo(result.rows().get(0).amount()));
  }

  @Test
  void keepsQuotedDescriptorsWithCommasIntact() {
    String csv =
        """
        Date,Description,Amount
        08/12/2026,"JOE'S BAR & GRILL, BROOKLYN",82.00
        """;

    ParseResult result = parse(csv, null);

    assertEquals(1, result.rows().size());
    assertEquals("JOE'S BAR & GRILL, BROOKLYN", result.rows().get(0).description());
  }

  @Test
  void countsUnparseableRowsInsteadOfFailingTheImport() {
    String csv =
        """
        Date,Description,Amount
        08/12/2026,GOOD ROW,20.00
        not-a-date,BAD DATE,20.00
        08/14/2026,BAD AMOUNT,abc
        08/15/2026,,30.00
        """;

    ParseResult result = parse(csv, null);

    // A partial parse is a success: three bad lines must not cost the user the good one.
    assertEquals(1, result.rows().size());
    assertEquals(4, result.totalRows());
    assertEquals(3, result.droppedRows());
  }

  @Test
  void readsCurrencySymbolsThousandsSeparatorsAndParentheses() {
    assertEquals(0, new BigDecimal("1234.56").compareTo(CsvStatementParser.parseAmount("$1,234.56")));
    assertEquals(0, new BigDecimal("-45.00").compareTo(CsvStatementParser.parseAmount("(45.00)")));
    assertEquals(0, new BigDecimal("-18.40").compareTo(CsvStatementParser.parseAmount("-$18.40")));
  }

  @Test
  void rejectsAFileWithNoRecognisableColumns() {
    String csv =
        """
        Column A,Column B,Column C
        foo,bar,baz
        """;

    StatementParseException thrown = assertThrows(StatementParseException.class, () -> parse(csv, null));
    assertTrue(thrown.getMessage().contains("Could not find"));
  }

  @Test
  void fallsBackToInferenceWhenTheProfileDoesNotMatchTheFile() {
    // The issuer changed its export, or the user picked the wrong profile. Inference is
    // still a better answer than an error.
    String csv =
        """
        Date,Merchant,Amount
        08/12/2026,BLUE BOTTLE,18.40
        """;

    ParseResult result = parse(csv, "chase");

    assertEquals(1, result.rows().size());
    assertEquals("BLUE BOTTLE", result.rows().get(0).description());
  }

  @Test
  void recognisesCsvUploadsByExtensionWhateverTheBrowserClaims() {
    assertTrue(parser.supports("application/vnd.ms-excel", "statement.csv"));
    assertTrue(parser.supports("text/csv", "statement.csv"));
    assertTrue(parser.supports(null, "STATEMENT.CSV"));
    org.junit.jupiter.api.Assertions.assertFalse(parser.supports("application/pdf", "statement.pdf"));
  }
}

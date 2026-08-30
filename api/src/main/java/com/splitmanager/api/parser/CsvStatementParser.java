package com.splitmanager.api.parser;

import com.splitmanager.api.exception.StatementParseException;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Deterministic CSV extraction (LLD 6.1).
 *
 * <p>With an issuer profile the column mapping is exact. Without one, columns are
 * inferred from the header row by name, which covers most exports; the one thing
 * inference cannot recover is the sign convention, so an unprofiled file is read as
 * "purchases are positive" and the user can re-import under a profile if it comes out
 * inverted.
 *
 * <p>Credits are discarded — a refund or a card payment is not something you can split.
 */
@Component
public class CsvStatementParser implements StatementParser {

  /** Tried in order when no profile pins the format. Most US issuers use one of these. */
  private static final List<String> DATE_FORMATS =
      List.of("MM/dd/yyyy", "M/d/yyyy", "yyyy-MM-dd", "MM/dd/yy", "dd/MM/yyyy", "MMM d, yyyy");

  private static final List<String> DATE_HEADERS =
      List.of("transaction date", "trans. date", "trans date", "posted date", "post date", "date");

  private static final List<String> DESCRIPTION_HEADERS =
      List.of("description", "merchant", "payee", "name", "details", "memo");

  private static final List<String> AMOUNT_HEADERS =
      List.of("amount", "debit", "withdrawal", "charge", "transaction amount");

  private static final List<String> CREDIT_HEADERS =
      List.of("credit", "deposit", "transaction type", "debit/credit", "type");

  private final IssuerProfileRegistry profiles;

  public CsvStatementParser(IssuerProfileRegistry profiles) {
    this.profiles = profiles;
  }

  @Override
  public boolean supports(String contentType, String fileName) {
    if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      return true;
    }
    // Browsers are inconsistent about the CSV content type - some send text/plain, and
    // Windows sends application/vnd.ms-excel for a .csv. The extension is the reliable
    // signal, so the content type is only a fallback.
    return contentType != null && (contentType.contains("csv") || contentType.equals("text/plain"));
  }

  @Override
  public ParseResult parse(byte[] bytes, String issuerProfileId) {
    IssuerProfile profile = profiles.find(issuerProfileId).orElse(null);

    List<RawStatementRow> rows = new ArrayList<>();
    int total = 0;
    int credits = 0;
    int dropped = 0;

    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .build();

    try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
        CSVParser parser = format.parse(reader)) {

      Mapping mapping = resolveColumns(parser.getHeaderNames(), profile);

      for (CSVRecord record : parser) {
        total++;
        try {
          RawStatementRow row = toRow(record, mapping.columns(), mapping.profile());
          if (row == null) {
            dropped++;
          } else if (row.credit()) {
            credits++;
          } else {
            rows.add(row);
          }
        } catch (RuntimeException e) {
          // One malformed line does not invalidate the file. Count it and keep going -
          // the count surfaces on the import as a partial-parse warning.
          dropped++;
        }
      }
    } catch (StatementParseException e) {
      throw e;
    } catch (Exception e) {
      throw new StatementParseException("Could not read the CSV file: " + e.getMessage());
    }

    return new ParseResult(rows, total, credits, dropped);
  }

  private RawStatementRow toRow(CSVRecord record, Columns columns, IssuerProfile profile) {
    String rawDate = value(record, columns.date());
    String description = value(record, columns.description());
    String rawAmount = value(record, columns.amount());

    if (rawDate == null || description == null || description.isBlank()) {
      return null;
    }

    boolean credit = false;
    String creditMarker = columns.credit() == null ? null : value(record, columns.credit());
    if (creditMarker != null && !creditMarker.isBlank()) {
      // Two shapes, both in the wild: a direction word, or a separate credit-amount
      // column that is only populated on refunds.
      String marker = creditMarker.toLowerCase(Locale.ROOT);
      if (marker.contains("credit") || marker.contains("payment") || marker.equals("cr")) {
        credit = true;
      } else if (parseAmount(creditMarker) != null) {
        credit = true;
        rawAmount = creditMarker;
      }
    }

    if (rawAmount == null || rawAmount.isBlank()) {
      return null;
    }

    BigDecimal amount = parseAmount(rawAmount);
    if (amount == null || amount.signum() == 0) {
      return null;
    }

    if (!credit) {
      // With a profile the sign convention is known. Without one, assume the common case
      // (purchases positive) and treat a negative as a credit.
      boolean debitsPositive = profile == null || profile.debitsArePositive();
      credit = debitsPositive ? amount.signum() < 0 : amount.signum() > 0;
    }

    LocalDate date = parseDate(rawDate, profile == null ? null : profile.dateFormat());
    if (date == null) {
      return null;
    }

    return new RawStatementRow(date, description.trim(), amount.abs(), credit);
  }

  private Mapping resolveColumns(List<String> headers, IssuerProfile profile) {
    if (profile != null) {
      String date = matchExact(headers, profile.dateColumn());
      String description = matchExact(headers, profile.descriptionColumn());
      String amount = matchExact(headers, profile.amountColumn());
      if (date != null && description != null && amount != null) {
        return new Mapping(
            new Columns(date, description, amount, matchExact(headers, profile.debitCreditColumn())),
            profile);
      }
      // The profile named columns this file does not have - the issuer changed its
      // export, or the wrong profile was picked. Falling through to inference is a
      // better answer than an error, since it is what an unprofiled import would do.
      //
      // The profile is dropped entirely here rather than kept for its sign convention:
      // a profile that does not describe this file's columns is no evidence about which
      // way its amounts point, and applying it anyway inverts the whole statement.
      profile = null;
    }

    String date = infer(headers, DATE_HEADERS);
    String description = infer(headers, DESCRIPTION_HEADERS);
    String amount = infer(headers, AMOUNT_HEADERS);

    if (date == null || description == null || amount == null) {
      throw new StatementParseException(
          "Could not find date, description, and amount columns in the CSV header: " + headers);
    }
    String credit = infer(headers, CREDIT_HEADERS);
    // "Debit" can infer as both the amount and the credit column on a two-column export.
    return new Mapping(new Columns(date, description, amount, amount.equals(credit) ? null : credit), null);
  }

  private static String matchExact(List<String> headers, String wanted) {
    if (wanted == null) {
      return null;
    }
    return headers.stream().filter(h -> h.trim().equalsIgnoreCase(wanted.trim())).findFirst().orElse(null);
  }

  /** Exact header match first, then substring - "Amount (USD)" should still be Amount. */
  private static String infer(List<String> headers, List<String> wanted) {
    for (String candidate : wanted) {
      Optional<String> exact =
          headers.stream().filter(h -> h.trim().equalsIgnoreCase(candidate)).findFirst();
      if (exact.isPresent()) {
        return exact.get();
      }
    }
    for (String candidate : wanted) {
      Optional<String> partial =
          headers.stream().filter(h -> h.toLowerCase(Locale.ROOT).contains(candidate)).findFirst();
      if (partial.isPresent()) {
        return partial.get();
      }
    }
    return null;
  }

  private static String value(CSVRecord record, String column) {
    if (column == null || !record.isMapped(column) || !record.isSet(column)) {
      return null;
    }
    String value = record.get(column);
    return value == null ? null : value.trim();
  }

  /** Handles currency symbols, thousands separators, and accounting-style parentheses. */
  static BigDecimal parseAmount(String raw) {
    String cleaned = raw.trim();
    boolean parenthesised = cleaned.startsWith("(") && cleaned.endsWith(")");
    if (parenthesised) {
      cleaned = cleaned.substring(1, cleaned.length() - 1);
    }
    cleaned = cleaned.replaceAll("[^0-9.\\-]", "");
    if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
      return null;
    }
    try {
      BigDecimal amount = new BigDecimal(cleaned);
      return parenthesised ? amount.negate() : amount;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static LocalDate parseDate(String raw, String preferredFormat) {
    List<String> formats = new ArrayList<>();
    if (preferredFormat != null && !preferredFormat.isBlank()) {
      formats.add(preferredFormat);
    }
    formats.addAll(DATE_FORMATS);

    for (String format : formats) {
      try {
        return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
      } catch (RuntimeException ignored) {
        // Try the next pattern.
      }
    }
    return null;
  }

  private record Columns(String date, String description, String amount, String credit) {}

  /** The resolved columns plus the profile that actually applies — null when inferred. */
  private record Mapping(Columns columns, IssuerProfile profile) {}
}

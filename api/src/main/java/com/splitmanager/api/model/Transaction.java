package com.splitmanager.api.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * One entry in the ledger — a split or a reimbursement (LLD 3.5).
 *
 * <p>Permanent. Replaces v1's ReceiptSession, which carried a TTL and deleted itself.
 * There is no expiry attribute here by design: the ledger, balances, statuses, and
 * duplicate detection all depend on records surviving.
 *
 * <p>Key layout (LLD 4.2) — this table is shared with {@link Person}, so keys are
 * prefixed by entity type rather than being bare ids.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Transaction {

  public static final String GSI1 = "GSI1";
  public static final String GSI2 = "GSI2";

  private String pk;
  private String sk;
  private String gsi1pk;
  private String gsi1sk;
  private String gsi2pk;
  private String gsi2sk;

  private String transactionId;
  /** Cognito {@code sub}. Never taken from the request body. */
  private String userId;
  private TransactionType type;
  private TransactionStatus status;

  private String merchant;
  /**
   * Receipt date, or the posting date from a statement. Part of the dedup key, so it is
   * the date the charge happened rather than when the row was created.
   */
  private LocalDate transactionDate;

  private Instant createdAt;
  private Instant updatedAt;

  private BigDecimal subtotal;
  private BigDecimal tax;
  private BigDecimal tip;
  private BigDecimal total;

  /** Empty for statement-derived and reimbursement transactions. */
  private List<LineItem> items;

  private String receiptImageS3Key;

  private SplitDefinition splitDefinition;
  private FinalizedSplit finalizedSplit;

  /** Provenance when this came from a statement import. Null for receipt-sourced rows. */
  private String sourceStatementImportId;

  private String notes;

  @DynamoDbPartitionKey
  public String getPk() {
    return pk;
  }

  @DynamoDbSortKey
  public String getSk() {
    return sk;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = GSI1)
  public String getGsi1pk() {
    return gsi1pk;
  }

  @DynamoDbSecondarySortKey(indexNames = GSI1)
  public String getGsi1sk() {
    return gsi1sk;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = GSI2)
  public String getGsi2pk() {
    return gsi2pk;
  }

  @DynamoDbSecondarySortKey(indexNames = GSI2)
  public String getGsi2sk() {
    return gsi2sk;
  }

  public static String pkFor(String transactionId) {
    return "TXN#" + transactionId;
  }

  public static final String SK_META = "META";

  public static String gsi1pkFor(String userId) {
    return "USER#" + userId + "#TXN";
  }

  /** ISO date first so lexical sort order is chronological order. */
  public static String gsi1skFor(LocalDate date, String transactionId) {
    return date + "#" + transactionId;
  }

  /**
   * Dedup partition: everything charged for the same amount on the same day (LLD 4.2).
   * A query on this returns the date+amount candidate set; merchant similarity is then
   * scored in process, because statement descriptors rarely match receipt vendor names
   * exactly.
   */
  public static String gsi2pkFor(String userId, BigDecimal amount, LocalDate date) {
    return "USER#" + userId + "#DEDUP#" + toCents(amount) + "#" + date;
  }

  public static String gsi2skFor(String merchant) {
    return normalizeMerchant(merchant);
  }

  /** Integer cents, so key equality is not at the mercy of BigDecimal scale. */
  public static long toCents(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
  }

  /**
   * Lowercase, strip payment-processor prefixes, drop punctuation, collapse whitespace.
   * "SQ *Blue Bottle Coffee" and "BLUE BOTTLE COFFEE" both land on "blue bottle coffee".
   */
  public static String normalizeMerchant(String merchant) {
    if (merchant == null || merchant.isBlank()) {
      return "";
    }
    String s = merchant.toLowerCase(Locale.ROOT).trim();
    s = s.replaceAll("^(sq \\*|tst\\*|sp |paypal \\*|pp\\*|dd \\*|ext \\*)", "");
    s = s.replaceAll("[^a-z0-9 ]", " ");
    return s.replaceAll("\\s+", " ").trim();
  }

  /**
   * Recomputes every key attribute from the business fields. Must be called before any
   * write — the keys are derived data, and a stale gsi2pk silently breaks dedup.
   */
  @DynamoDbIgnore
  public void applyKeys() {
    this.pk = pkFor(transactionId);
    this.sk = SK_META;
    this.gsi1pk = gsi1pkFor(userId);
    this.gsi1sk = gsi1skFor(transactionDate, transactionId);
    if (total != null && transactionDate != null) {
      this.gsi2pk = gsi2pkFor(userId, total, transactionDate);
      this.gsi2sk = gsi2skFor(merchant);
    }
  }
}

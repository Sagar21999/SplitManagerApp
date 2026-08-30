package com.splitmanager.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * One debit row from a statement, waiting to be confirmed or dismissed (LLD 3.5).
 *
 * <p>A candidate is not a ledger entry. Nothing here counts toward balances until the
 * user confirms it, at which point a {@link Transaction} is created and its id recorded
 * in {@code resultingTransactionId}.
 *
 * <p>Key layout: {@code IMPORT#{importId}} / {@code CAND#{seq}}, sequence zero-padded so
 * that lexical order is statement order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class StatementCandidate {

  private String pk;
  private String sk;

  private String candidateId;
  private String importId;
  /** Position in the file; also the sort key, so candidates come back in file order. */
  private int sequence;

  private LocalDate date;
  /** The descriptor exactly as the issuer wrote it, kept for the user to recognise. */
  private String rawDescription;

  /** {@link Transaction#normalizeMerchant} applied to the descriptor. */
  private String normalizedMerchant;

  private BigDecimal amount;

  private CandidateClassification classification;
  private BigDecimal classificationConfidence;

  /** Empty when nothing similar was found. Warnings only — never auto-merged. */
  private List<DuplicateMatch> duplicateMatches;

  private CandidateStatus status;
  private String resultingTransactionId;

  @DynamoDbPartitionKey
  public String getPk() {
    return pk;
  }

  @DynamoDbSortKey
  public String getSk() {
    return sk;
  }

  public static final String SK_PREFIX = "CAND#";

  /** Zero-padded to four digits: a 1000-row statement would otherwise sort 10 before 9. */
  public static String skFor(int sequence) {
    return SK_PREFIX + String.format("%04d", sequence);
  }

  @DynamoDbIgnore
  public void applyKeys() {
    this.pk = StatementImport.pkFor(importId);
    this.sk = skFor(sequence);
  }
}

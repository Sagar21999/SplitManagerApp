package com.splitmanager.api.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * One uploaded statement file (LLD 3.5). The file itself does not survive the request —
 * {@code StatementIngestionService} deletes the S3 object once extraction finishes, so
 * this record is all that remains of it.
 *
 * <p>Key layout: {@code IMPORT#{importId}} / {@code META}, sharing a partition with the
 * candidates so one Query returns the import and every row it produced.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class StatementImport {

  private String pk;
  private String sk;

  private String importId;
  /** Cognito {@code sub}. */
  private String userId;
  private String fileName;
  private String contentType;
  /** Id of the issuer profile used, or null when the columns were inferred. */
  private String issuerProfile;
  private Instant uploadedAt;

  /** Rows the parser saw, including the credits and unparseable lines it discarded. */
  private int rowCount;

  /** Rows that became reviewable candidates. */
  private int candidateCount;

  private StatementImportStatus status;

  /**
   * Why the import failed, or — on a partial success — what was dropped. A READY import
   * with a failureReason is normal: 40 of 45 rows parsed is more useful than an error.
   */
  private String failureReason;

  @DynamoDbPartitionKey
  public String getPk() {
    return pk;
  }

  @DynamoDbSortKey
  public String getSk() {
    return sk;
  }

  public static String pkFor(String importId) {
    return "IMPORT#" + importId;
  }

  public static final String SK_META = "META";

  @DynamoDbIgnore
  public void applyKeys() {
    this.pk = pkFor(importId);
    this.sk = SK_META;
  }
}

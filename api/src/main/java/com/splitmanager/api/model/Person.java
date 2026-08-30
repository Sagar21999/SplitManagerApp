package com.splitmanager.api.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Someone the user has split with (BRD FR13). Saved automatically the first time a name
 * is used, then offered for one-tap selection on later transactions.
 *
 * <p>These are names, not accounts - nobody here can log in. See BRD "Target user".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Person {

  private String pk;
  private String sk;

  private String personId;
  private String userId;
  private String displayName;
  private Instant createdAt;
  /** Drives most-recently-used ordering in the picker. */
  private Instant lastUsedAt;
  /**
   * Soft delete. Historical transactions keep the name they were finalized with, so a
   * removed person must still resolve for display.
   */
  private boolean archived;

  @DynamoDbPartitionKey
  public String getPk() {
    return pk;
  }

  @DynamoDbSortKey
  public String getSk() {
    return sk;
  }

  public static String pkFor(String userId) {
    return "USER#" + userId + "#PEOPLE";
  }

  public static String skFor(String personId) {
    return "PERSON#" + personId;
  }

  /** Populates the composite key attributes from the business fields before a write. */
  @DynamoDbIgnore
  public void applyKeys() {
    this.pk = pkFor(userId);
    this.sk = skFor(personId);
  }
}

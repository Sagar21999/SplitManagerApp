package com.splitmanager.api.repository;

import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionStatus;
import com.splitmanager.api.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

/**
 * Transaction persistence over the shared single table (LLD 4.2/4.4).
 *
 * <p>Direct reads are a GetItem on the transaction's own partition. Listing goes through
 * GSI1, which is keyed so that lexical order is date order. Duplicate detection goes
 * through GSI2, which partitions by amount+date.
 */
@Repository
public class TransactionRepository {

  private final DynamoDbTable<Transaction> table;
  private final DynamoDbIndex<Transaction> byDate;
  private final DynamoDbIndex<Transaction> byAmountAndDate;

  public TransactionRepository(
      DynamoDbEnhancedClient enhancedClient, @Value("${split-manager.table-name}") String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(Transaction.class));
    this.byDate = table.index(Transaction.GSI1);
    this.byAmountAndDate = table.index(Transaction.GSI2);
  }

  public void save(Transaction transaction) {
    // Keys are derived from the business fields, so they are recomputed on every write.
    // Skipping this leaves a stale gsi2pk behind and silently breaks duplicate detection.
    transaction.applyKeys();
    table.putItem(transaction);
  }

  public Optional<Transaction> findById(String transactionId) {
    Key key =
        Key.builder()
            .partitionValue(Transaction.pkFor(transactionId))
            .sortValue(Transaction.SK_META)
            .build();
    return Optional.ofNullable(table.getItem(key));
  }

  /**
   * Newest first. Status and type are applied in memory rather than as a DynamoDB
   * FilterExpression: at personal scale the whole partition is a few hundred items, and
   * filtering server-side would still read them all while making pagination behave
   * unintuitively (a page could come back empty with more results behind it).
   */
  public List<Transaction> listByDateDesc(
      String userId, TransactionStatus status, TransactionType type, int limit) {
    QueryConditional conditional =
        QueryConditional.keyEqualTo(
            Key.builder().partitionValue(Transaction.gsi1pkFor(userId)).build());

    List<Transaction> results = new ArrayList<>();
    byDate
        .query(QueryEnhancedRequest.builder().queryConditional(conditional).scanIndexForward(false).build())
        .stream()
        .flatMap(page -> page.items().stream())
        .filter(t -> status == null || t.getStatus() == status)
        .filter(t -> type == null || t.getType() == type)
        .limit(limit)
        .forEach(results::add);
    return results;
  }

  /** Every transaction counting toward balances, regardless of type. */
  public List<Transaction> listAll(String userId) {
    return listByDateDesc(userId, null, null, Integer.MAX_VALUE);
  }

  /**
   * The date+amount candidate set for duplicate detection. Merchant similarity is scored
   * by the caller — statement descriptors ("SQ *BLUE BOTTLE COFF") rarely match receipt
   * vendor names ("Blue Bottle Coffee") closely enough for an exact key comparison.
   */
  public List<Transaction> findByAmountAndDate(String userId, BigDecimal amount, LocalDate date) {
    QueryConditional conditional =
        QueryConditional.keyEqualTo(
            Key.builder().partitionValue(Transaction.gsi2pkFor(userId, amount, date)).build());

    List<Transaction> results = new ArrayList<>();
    byAmountAndDate
        .query(QueryEnhancedRequest.builder().queryConditional(conditional).build())
        .stream()
        .flatMap(page -> page.items().stream())
        .forEach(results::add);
    return results;
  }

  public void delete(String transactionId) {
    table.deleteItem(
        Key.builder()
            .partitionValue(Transaction.pkFor(transactionId))
            .sortValue(Transaction.SK_META)
            .build());
  }
}

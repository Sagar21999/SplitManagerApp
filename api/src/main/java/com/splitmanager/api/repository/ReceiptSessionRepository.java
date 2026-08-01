package com.splitmanager.api.repository;

import com.splitmanager.api.model.ReceiptSession;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class ReceiptSessionRepository {

  private final DynamoDbTable<ReceiptSession> table;

  public ReceiptSessionRepository(
      DynamoDbEnhancedClient enhancedClient, @Value("${split-manager.table-name}") String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(ReceiptSession.class));
  }

  public void save(ReceiptSession session) {
    table.putItem(session);
  }

  public Optional<ReceiptSession> findById(String sessionId) {
    return Optional.ofNullable(table.getItem(Key.builder().partitionValue(sessionId).build()));
  }

  public void update(ReceiptSession session) {
    table.putItem(session);
  }
}

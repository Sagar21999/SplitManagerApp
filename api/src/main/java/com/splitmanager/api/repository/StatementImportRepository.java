package com.splitmanager.api.repository;

import com.splitmanager.api.model.StatementCandidate;
import com.splitmanager.api.model.StatementImport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

/**
 * Statement imports and their candidates (LLD 4.4).
 *
 * <p>Both live in the same partition, so the import and every row it produced come back
 * from a single Query. Two table views over the same physical table give the enhanced
 * client a schema for each bean.
 */
@Repository
public class StatementImportRepository {

  /** DynamoDB's hard limit on items per BatchWriteItem call. */
  private static final int BATCH_LIMIT = 25;

  private final DynamoDbEnhancedClient enhancedClient;
  private final DynamoDbTable<StatementImport> imports;
  private final DynamoDbTable<StatementCandidate> candidates;

  public StatementImportRepository(
      DynamoDbEnhancedClient enhancedClient, @Value("${split-manager.table-name}") String tableName) {
    this.enhancedClient = enhancedClient;
    this.imports = enhancedClient.table(tableName, TableSchema.fromBean(StatementImport.class));
    this.candidates = enhancedClient.table(tableName, TableSchema.fromBean(StatementCandidate.class));
  }

  public void saveImport(StatementImport statementImport) {
    statementImport.applyKeys();
    imports.putItem(statementImport);
  }

  public Optional<StatementImport> findImport(String importId) {
    Key key =
        Key.builder()
            .partitionValue(StatementImport.pkFor(importId))
            .sortValue(StatementImport.SK_META)
            .build();
    return Optional.ofNullable(imports.getItem(key));
  }

  /** Writes the whole batch, chunked to DynamoDB's 25-item limit. */
  public void saveCandidates(List<StatementCandidate> batch) {
    for (int start = 0; start < batch.size(); start += BATCH_LIMIT) {
      List<StatementCandidate> chunk = batch.subList(start, Math.min(start + BATCH_LIMIT, batch.size()));
      WriteBatch.Builder<StatementCandidate> writeBatch =
          WriteBatch.builder(StatementCandidate.class).mappedTableResource(candidates);
      chunk.forEach(
          candidate -> {
            candidate.applyKeys();
            writeBatch.addPutItem(candidate);
          });
      enhancedClient.batchWriteItem(r -> r.addWriteBatch(writeBatch.build()));
    }
  }

  /** In file order — the sort key is the zero-padded row sequence. */
  public List<StatementCandidate> findCandidates(String importId) {
    QueryConditional conditional =
        QueryConditional.sortBeginsWith(
            Key.builder()
                .partitionValue(StatementImport.pkFor(importId))
                .sortValue(StatementCandidate.SK_PREFIX)
                .build());

    List<StatementCandidate> results = new ArrayList<>();
    candidates.query(r -> r.queryConditional(conditional)).stream()
        .flatMap(page -> page.items().stream())
        .forEach(results::add);
    return results;
  }

  public Optional<StatementCandidate> findCandidate(String importId, String candidateId) {
    // Candidates are keyed by sequence rather than id, so this is a partition scan of at
    // most a few hundred rows rather than a GetItem. Adding a third GSI to save that
    // would cost more than it buys at personal volume.
    return findCandidates(importId).stream()
        .filter(candidate -> candidate.getCandidateId().equals(candidateId))
        .findFirst();
  }

  public void updateCandidate(StatementCandidate candidate) {
    candidate.applyKeys();
    candidates.putItem(candidate);
  }
}

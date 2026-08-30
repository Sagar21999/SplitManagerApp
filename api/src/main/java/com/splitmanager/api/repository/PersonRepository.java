package com.splitmanager.api.repository;

import com.splitmanager.api.model.Person;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

/**
 * The saved people directory (BRD FR13).
 *
 * <p>Shares the physical table with {@link com.splitmanager.api.model.Transaction} but
 * uses its own bean schema — the Enhanced Client is happy to map several beans onto one
 * table as long as their key prefixes keep them apart.
 */
@Repository
public class PersonRepository {

  private final DynamoDbTable<Person> table;

  public PersonRepository(
      DynamoDbEnhancedClient enhancedClient, @Value("${split-manager.table-name}") String tableName) {
    this.table = enhancedClient.table(tableName, TableSchema.fromBean(Person.class));
  }

  public void save(Person person) {
    person.applyKeys();
    table.putItem(person);
  }

  /** Active people, most recently used first — the order the picker wants. */
  public List<Person> findAll(String userId) {
    List<Person> people = new ArrayList<>(queryAll(userId));
    people.removeIf(Person::isArchived);
    people.sort(
        Comparator.comparing(
                Person::getLastUsedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Person::getDisplayName, Comparator.nullsLast(Comparator.naturalOrder())));
    return people;
  }

  /** Includes archived people: historical transactions still need their names resolved. */
  public List<Person> findAllIncludingArchived(String userId) {
    return queryAll(userId);
  }

  public Optional<Person> findById(String userId, String personId) {
    Key key =
        Key.builder()
            .partitionValue(Person.pkFor(userId))
            .sortValue(Person.skFor(personId))
            .build();
    return Optional.ofNullable(table.getItem(key));
  }

  /** Case-insensitive, so "alex" does not create a second row alongside "Alex". */
  public Optional<Person> findByDisplayName(String userId, String displayName) {
    String target = displayName.trim().toLowerCase();
    return queryAll(userId).stream()
        .filter(p -> p.getDisplayName() != null && p.getDisplayName().trim().toLowerCase().equals(target))
        .findFirst();
  }

  public void delete(String userId, String personId) {
    table.deleteItem(
        Key.builder()
            .partitionValue(Person.pkFor(userId))
            .sortValue(Person.skFor(personId))
            .build());
  }

  private List<Person> queryAll(String userId) {
    QueryConditional conditional =
        QueryConditional.keyEqualTo(Key.builder().partitionValue(Person.pkFor(userId)).build());
    List<Person> results = new ArrayList<>();
    table.query(conditional).stream().flatMap(page -> page.items().stream()).forEach(results::add);
    return results;
  }
}

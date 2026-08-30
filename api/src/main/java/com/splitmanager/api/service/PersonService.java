package com.splitmanager.api.service;

import com.splitmanager.api.exception.PersonNotFoundException;
import com.splitmanager.api.exception.ValidationException;
import com.splitmanager.api.model.Person;
import com.splitmanager.api.repository.PersonRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The saved people directory (BRD FR13).
 *
 * <p>The directory is meant to fill itself: a name typed once on a transaction is
 * remembered and offered thereafter, so the user never maintains it as a separate chore.
 * {@link #resolveOrCreate} is what makes that happen, and finalizing a transaction calls
 * it.
 */
@Service
public class PersonService {

  private final PersonRepository repository;

  public PersonService(PersonRepository repository) {
    this.repository = repository;
  }

  public List<Person> list(String userId) {
    return repository.findAll(userId);
  }

  public Person create(String userId, String displayName) {
    String name = requireName(displayName);
    return repository
        .findByDisplayName(userId, name)
        .orElseGet(
            () -> {
              Person person = new Person();
              person.setPersonId(UUID.randomUUID().toString());
              person.setUserId(userId);
              person.setDisplayName(name);
              person.setCreatedAt(Instant.now());
              person.setLastUsedAt(Instant.now());
              person.setArchived(false);
              repository.save(person);
              return person;
            });
  }

  public Person rename(String userId, String personId, String displayName) {
    Person person =
        repository.findById(userId, personId).orElseThrow(() -> new PersonNotFoundException(personId));
    person.setDisplayName(requireName(displayName));
    repository.save(person);
    return person;
  }

  /**
   * Archives rather than deletes. A hard delete would strand every historical transaction
   * that references this id, leaving amounts owed to nobody.
   */
  public void archive(String userId, String personId) {
    Person person =
        repository.findById(userId, personId).orElseThrow(() -> new PersonNotFoundException(personId));
    person.setArchived(true);
    repository.save(person);
  }

  /**
   * Maps free-text names to person ids, creating rows for names not seen before and
   * touching {@code lastUsedAt} on the ones that were. This is what keeps the picker
   * ordered by who the user actually splits with most recently.
   */
  public List<Person> resolveOrCreate(String userId, List<String> displayNames) {
    List<Person> resolved = new ArrayList<>();
    if (displayNames == null) {
      return resolved;
    }
    for (String rawName : displayNames) {
      if (rawName == null || rawName.isBlank()) {
        continue;
      }
      Person person = create(userId, rawName);
      person.setLastUsedAt(Instant.now());
      repository.save(person);
      resolved.add(person);
    }
    return resolved;
  }

  /** Names for display, including archived people, so old transactions still render. */
  public List<Person> listIncludingArchived(String userId) {
    return repository.findAllIncludingArchived(userId);
  }

  private String requireName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      throw new ValidationException("A name is required.");
    }
    return displayName.trim();
  }
}

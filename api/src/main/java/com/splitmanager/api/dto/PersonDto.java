package com.splitmanager.api.dto;

import com.splitmanager.api.model.Person;
import java.time.Instant;

public record PersonDto(String personId, String displayName, Instant lastUsedAt) {
  public static PersonDto from(Person p) {
    return new PersonDto(p.getPersonId(), p.getDisplayName(), p.getLastUsedAt());
  }
}

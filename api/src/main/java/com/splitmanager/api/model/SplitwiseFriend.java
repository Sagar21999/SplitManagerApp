package com.splitmanager.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplitwiseFriend {
  private long splitwiseId;
  private String firstName;
  private String lastName;
  private String avatarUrl;
}

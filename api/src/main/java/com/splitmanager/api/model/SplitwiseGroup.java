package com.splitmanager.api.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplitwiseGroup {
  private long id;
  private String name;
  private List<SplitwiseFriend> members;
}

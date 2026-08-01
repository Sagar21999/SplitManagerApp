package com.splitmanager.api.dto;

import com.splitmanager.api.model.SplitwiseFriend;
import com.splitmanager.api.model.SplitwiseGroup;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendsResponse {
  private List<SplitwiseFriend> friends;
  private List<SplitwiseGroup> groups;
}

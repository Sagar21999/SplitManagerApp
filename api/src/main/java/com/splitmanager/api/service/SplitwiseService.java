package com.splitmanager.api.service;

import com.splitmanager.api.client.SplitwiseClient;
import com.splitmanager.api.client.SplitwiseRequestBuilder;
import com.splitmanager.api.dto.SplitwiseExpenseRequestDto;
import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.ReceiptSession;
import com.splitmanager.api.model.SplitwiseFriend;
import com.splitmanager.api.model.SplitwiseGroup;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SplitwiseService {

  private final SplitwiseClient splitwiseClient;
  private final SplitwiseRequestBuilder requestBuilder;

  public SplitwiseService(SplitwiseClient splitwiseClient, SplitwiseRequestBuilder requestBuilder) {
    this.splitwiseClient = splitwiseClient;
    this.requestBuilder = requestBuilder;
  }

  public record SplitwiseFriendsAndGroups(List<SplitwiseFriend> friends, List<SplitwiseGroup> groups) {}

  public SplitwiseFriendsAndGroups getFriendsAndGroups() {
    return new SplitwiseFriendsAndGroups(splitwiseClient.getFriends(), splitwiseClient.getGroups());
  }

  public String createExpense(ReceiptSession session, FinalizedSplit split, byte[] imageBytes) {
    SplitwiseExpenseRequestDto request = requestBuilder.build(session, split);
    return splitwiseClient.createExpense(request, imageBytes, session.getReceiptImageContentType());
  }
}

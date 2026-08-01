package com.splitmanager.api.client;

import com.splitmanager.api.dto.SplitwiseExpenseRequestDto;
import com.splitmanager.api.model.SplitwiseFriend;
import com.splitmanager.api.model.SplitwiseGroup;
import java.util.List;

public interface SplitwiseClient {
  List<SplitwiseFriend> getFriends();

  List<SplitwiseGroup> getGroups();

  /** Returns the created Splitwise expense ID. */
  String createExpense(SplitwiseExpenseRequestDto request, byte[] receiptImageBytes, String receiptImageContentType);
}

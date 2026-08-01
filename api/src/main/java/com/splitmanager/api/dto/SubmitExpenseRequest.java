package com.splitmanager.api.dto;

import com.splitmanager.api.model.FinalizedSplit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitExpenseRequest {
  private String sessionId;
  private FinalizedSplit split;
}

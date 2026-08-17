package com.splitmanager.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeSplitResponse {
  private boolean success;
  private SplitSummaryDto summary;
  private String error;
}

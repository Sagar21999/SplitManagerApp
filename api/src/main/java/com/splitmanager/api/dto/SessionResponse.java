package com.splitmanager.api.dto;

import com.splitmanager.api.model.ReceiptItem;
import com.splitmanager.api.model.SessionStatus;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
  private String sessionId;
  private SessionStatus status;
  private String merchant;
  private List<ReceiptItem> items;
  private BigDecimal tax;
  private BigDecimal tip;
  private BigDecimal total;
}

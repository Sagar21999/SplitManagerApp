package com.splitmanager.api.dto;

import com.splitmanager.api.model.ReceiptItem;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseReceiptResponse {
  private String sessionId;
  private String merchant;
  private List<ReceiptItem> items;
  private BigDecimal tax;
  private BigDecimal tip;
  private BigDecimal total;
  private String url;
}

package com.splitmanager.api.service;

import com.splitmanager.api.model.ReceiptItem;
import java.math.BigDecimal;
import java.util.List;

public record ParsedReceipt(String merchant, List<ReceiptItem> items, BigDecimal tax, BigDecimal total) {}

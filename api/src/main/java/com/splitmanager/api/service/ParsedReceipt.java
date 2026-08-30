package com.splitmanager.api.service;

import com.splitmanager.api.model.LineItem;
import java.math.BigDecimal;
import java.util.List;

public record ParsedReceipt(String merchant, List<LineItem> items, BigDecimal tax, BigDecimal total) {}

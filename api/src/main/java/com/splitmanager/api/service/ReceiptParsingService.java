package com.splitmanager.api.service;

import com.splitmanager.api.client.TextractClient;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;

@Service
public class ReceiptParsingService {

  private final TextractClient textractClient;

  public ReceiptParsingService(TextractClient textractClient) {
    this.textractClient = textractClient;
  }

  public ParsedReceipt parse(byte[] imageBytes, String contentType) {
    ExpenseDocument doc = textractClient.analyzeExpense(imageBytes);

    String merchant = textractClient.extractSummaryField(doc, "VENDOR_NAME").orElse(null);
    BigDecimal tax = textractClient.extractSummaryField(doc, "TAX").map(this::parseAmount).orElse(null);
    BigDecimal total = textractClient.extractSummaryField(doc, "TOTAL").map(this::parseAmount).orElse(null);

    return new ParsedReceipt(merchant, textractClient.extractLineItems(doc), tax, total);
  }

  private BigDecimal parseAmount(String raw) {
    try {
      return new BigDecimal(raw.replaceAll("[^0-9.]", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

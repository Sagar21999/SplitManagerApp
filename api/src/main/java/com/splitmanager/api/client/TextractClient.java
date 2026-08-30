package com.splitmanager.api.client;

import com.splitmanager.api.model.LineItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.model.AnalyzeExpenseRequest;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.LineItemFields;
import software.amazon.awssdk.services.textract.model.LineItemGroup;

@Component
public class TextractClient {

  private final software.amazon.awssdk.services.textract.TextractClient textract;

  public TextractClient(software.amazon.awssdk.services.textract.TextractClient textract) {
    this.textract = textract;
  }

  public ExpenseDocument analyzeExpense(byte[] imageBytes) {
    var response =
        textract.analyzeExpense(
            AnalyzeExpenseRequest.builder()
                .document(Document.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build())
                .build());
    return response.expenseDocuments().stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Textract returned no expense documents"));
  }

  public Optional<String> extractSummaryField(ExpenseDocument doc, String fieldType) {
    return doc.summaryFields().stream()
        .filter(f -> f.type() != null && fieldType.equals(f.type().text()))
        .findFirst()
        .map(ExpenseField::valueDetection)
        .map(v -> v.text());
  }

  public List<LineItem> extractLineItems(ExpenseDocument doc) {
    List<LineItem> items = new ArrayList<>();
    for (LineItemGroup group : doc.lineItemGroups()) {
      for (LineItemFields lineItem : group.lineItems()) {
        String name = null;
        BigDecimal price = null;
        for (ExpenseField field : lineItem.lineItemExpenseFields()) {
          if (field.type() == null || field.valueDetection() == null) {
            continue;
          }
          String type = field.type().text();
          String value = field.valueDetection().text();
          if ("ITEM".equals(type)) {
            name = value;
          } else if ("PRICE".equals(type)) {
            price = parsePrice(value);
          }
        }
        if (name != null || price != null) {
          items.add(new LineItem(UUID.randomUUID().toString(), name, price));
        }
      }
    }
    return items;
  }

  private BigDecimal parsePrice(String raw) {
    try {
      return new BigDecimal(raw.replaceAll("[^0-9.]", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

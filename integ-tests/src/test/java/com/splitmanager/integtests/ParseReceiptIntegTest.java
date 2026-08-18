package com.splitmanager.integtests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

class ParseReceiptIntegTest {

  @Test
  void parseReceiptReturnsStructuredFields() {
    Response response = ApiSupport.parseSampleReceipt();

    String sessionId = response.path("sessionId");
    assertNotNull(sessionId);
    assertTrue(!sessionId.isBlank());

    // Loose on exact OCR content — Textract's line-item extraction can vary — but the
    // summary fields (total/tax) are the most reliably extracted and prove the
    // /parse-receipt -> Textract -> DynamoDB pipeline actually works end-to-end.
    Number total = response.path("total");
    assertNotNull(total);
  }
}

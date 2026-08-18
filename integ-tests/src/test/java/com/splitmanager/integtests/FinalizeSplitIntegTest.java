package com.splitmanager.integtests;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.response.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinalizeSplitIntegTest {

  @Test
  void finalizeSplitReturnsCorrectSummary() {
    Response parseResponse = ApiSupport.parseSampleReceipt();
    String sessionId = parseResponse.path("sessionId");
    double total = ((Number) parseResponse.path("total")).doubleValue();

    BigDecimal aliceShare = BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP).divide(BigDecimal.valueOf(2));
    BigDecimal bobShare = BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP).subtract(aliceShare);

    Map<String, Object> requestBody =
        Map.of(
            "sessionId", sessionId,
            "split",
                Map.of(
                    "mode", "EQUAL",
                    "participantShares", Map.of("alice", aliceShare, "bob", bobShare),
                    "payerId", "alice"));

    Response response =
        given()
            .baseUri(ApiSupport.baseUri())
            .contentType("application/json")
            .body(requestBody)
            .when()
            .post("/finalize-split")
            .then()
            .statusCode(200)
            .extract()
            .response();

    assertTrue(response.path("success"));
    double owedAlice = ((Number) response.path("summary.amountOwedByParticipant.alice")).doubleValue();
    double owedBob = ((Number) response.path("summary.amountOwedByParticipant.bob")).doubleValue();
    assertEquals(aliceShare.doubleValue(), owedAlice, 0.001);
    assertEquals(bobShare.doubleValue(), owedBob, 0.001);

    String shareText = response.path("summary.shareText");
    assertTrue(shareText.contains("alice paid"));
    assertTrue(shareText.contains("bob"));
  }
}

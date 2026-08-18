package com.splitmanager.integtests;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

class SessionIntegTest {

  @Test
  void getSessionReturnsPreviouslyParsedData() {
    Response parseResponse = ApiSupport.parseSampleReceipt();
    String sessionId = parseResponse.path("sessionId");

    Response sessionResponse =
        given()
            .baseUri(ApiSupport.baseUri())
            .when()
            .get("/session/{sessionId}", sessionId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    assertEquals(sessionId, sessionResponse.path("sessionId"));
    assertEquals("PARSED", sessionResponse.path("status"));
    Number parseTotal = parseResponse.path("total");
    Number sessionTotal = sessionResponse.path("total");
    assertEquals(parseTotal.doubleValue(), sessionTotal.doubleValue(), 0.001);
  }
}

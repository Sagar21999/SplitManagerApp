package com.splitmanager.integtests;

import static io.restassured.RestAssured.given;

import io.restassured.response.Response;
import java.io.InputStream;

/** Shared helpers for hitting live Beta's API from the integ tests below. */
final class ApiSupport {

  private ApiSupport() {}

  static String baseUri() {
    String baseUrl = System.getProperty("beta.api.url");
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("beta.api.url system property must be set");
    }
    return baseUrl;
  }

  /** POSTs the bundled sample receipt image to /parse-receipt and returns the response. */
  static Response parseSampleReceipt() {
    InputStream image = ApiSupport.class.getClassLoader().getResourceAsStream("sample-receipt.jpg");
    if (image == null) {
      throw new IllegalStateException("sample-receipt.jpg missing from test resources");
    }
    return given()
        .baseUri(baseUri())
        .multiPart("image", "sample-receipt.jpg", image, "image/jpeg")
        .when()
        .post("/parse-receipt")
        .then()
        .statusCode(200)
        .extract()
        .response();
  }
}

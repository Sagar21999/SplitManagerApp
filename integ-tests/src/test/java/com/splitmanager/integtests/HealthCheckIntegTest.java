package com.splitmanager.integtests;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Placeholder for the Phase 5 integ-test suite (see docs/tasks.md). Confirms the
 * deployed API is actually reachable so the pipeline's IntegTests gate has something
 * real to check before Prod promotion, ahead of ParseReceiptIntegTest/SessionIntegTest/
 * FinalizeSplitIntegTest being written.
 */
class HealthCheckIntegTest {

  @Test
  void apiHealthEndpointReturnsOk() throws Exception {
    String baseUrl = System.getProperty("beta.api.url");
    assertEquals(true, baseUrl != null && !baseUrl.isBlank(), "beta.api.url system property must be set");

    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/actuator/health"))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode(), "expected /actuator/health to return 200, body: " + response.body());
  }
}

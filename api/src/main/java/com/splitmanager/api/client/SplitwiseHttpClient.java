package com.splitmanager.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitmanager.api.config.SecretsConfig.SplitwiseApiKeySupplier;
import com.splitmanager.api.dto.SplitwiseExpenseRequestDto;
import com.splitmanager.api.exception.SplitwiseApiException;
import com.splitmanager.api.model.SplitwiseFriend;
import com.splitmanager.api.model.SplitwiseGroup;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Production implementation — real HTTP calls to secure.splitwise.com/api/v3.0. */
@Component
public class SplitwiseHttpClient implements SplitwiseClient {

  private static final String BASE_URL = "https://secure.splitwise.com/api/v3.0";

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SplitwiseApiKeySupplier apiKeySupplier;

  public SplitwiseHttpClient(SplitwiseApiKeySupplier apiKeySupplier) {
    this.apiKeySupplier = apiKeySupplier;
  }

  @Override
  public List<SplitwiseFriend> getFriends() {
    JsonNode root = getJson("/get_friends");
    List<SplitwiseFriend> friends = new ArrayList<>();
    for (JsonNode f : root.path("friends")) {
      friends.add(
          new SplitwiseFriend(
              f.path("id").asLong(),
              f.path("first_name").asText(null),
              f.path("last_name").asText(null),
              f.path("picture").path("medium").asText(null)));
    }
    return friends;
  }

  @Override
  public List<SplitwiseGroup> getGroups() {
    JsonNode root = getJson("/get_groups");
    List<SplitwiseGroup> groups = new ArrayList<>();
    for (JsonNode g : root.path("groups")) {
      List<SplitwiseFriend> members = new ArrayList<>();
      for (JsonNode m : g.path("members")) {
        members.add(
            new SplitwiseFriend(
                m.path("id").asLong(),
                m.path("first_name").asText(null),
                m.path("last_name").asText(null),
                m.path("picture").path("medium").asText(null)));
      }
      groups.add(new SplitwiseGroup(g.path("id").asLong(), g.path("name").asText(null), members));
    }
    return groups;
  }

  @Override
  public String createExpense(
      SplitwiseExpenseRequestDto request, byte[] receiptImageBytes, String receiptImageContentType) {
    String boundary = "----SplitManagerBoundary" + UUID.randomUUID();
    byte[] body = buildMultipartBody(request, receiptImageBytes, receiptImageContentType, boundary);

    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/create_expense"))
            .header("Authorization", "Bearer " + requireApiKey())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(BodyPublishers.ofByteArray(body))
            .build();

    try {
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode expenses = root.path("expenses");
      if (response.statusCode() != 200 || !expenses.isArray() || expenses.isEmpty()) {
        throw new SplitwiseApiException("Splitwise create_expense failed: " + response.body());
      }
      return expenses.get(0).path("id").asText();
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SplitwiseApiException("Failed to call Splitwise create_expense", e);
    }
  }

  private JsonNode getJson(String path) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .header("Authorization", "Bearer " + requireApiKey())
            .GET()
            .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new SplitwiseApiException("Splitwise " + path + " failed: " + response.body());
      }
      return objectMapper.readTree(response.body());
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SplitwiseApiException("Failed to call Splitwise " + path, e);
    }
  }

  private String requireApiKey() {
    String key = apiKeySupplier.get();
    if (key == null || key.isBlank()) {
      throw new SplitwiseApiException("Splitwise API key is not configured");
    }
    return key;
  }

  private byte[] buildMultipartBody(
      SplitwiseExpenseRequestDto request, byte[] imageBytes, String imageContentType, String boundary) {
    var out = new java.io.ByteArrayOutputStream();
    try {
      writeField(out, boundary, "cost", request.getCost().toPlainString());
      writeField(out, boundary, "description", request.getDescription());
      int i = 0;
      for (Map.Entry<String, BigDecimal> entry : request.getPaidShare().entrySet()) {
        writeField(out, boundary, "users__" + i + "__user_id", entry.getKey());
        writeField(out, boundary, "users__" + i + "__paid_share", entry.getValue().toPlainString());
        writeField(
            out,
            boundary,
            "users__" + i + "__owed_share",
            request.getOwedShare().getOrDefault(entry.getKey(), BigDecimal.ZERO).toPlainString());
        i++;
      }
      if (imageBytes != null && imageBytes.length > 0) {
        writeFilePart(out, boundary, "receipt", "receipt.jpg", imageContentType, imageBytes);
      }
      out.write(("--" + boundary + "--\r\n").getBytes());
    } catch (IOException e) {
      throw new SplitwiseApiException("Failed to build Splitwise multipart request", e);
    }
    return out.toByteArray();
  }

  private void writeField(java.io.ByteArrayOutputStream out, String boundary, String name, String value)
      throws IOException {
    out.write(("--" + boundary + "\r\n").getBytes());
    out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
    out.write((value + "\r\n").getBytes());
  }

  private void writeFilePart(
      java.io.ByteArrayOutputStream out,
      String boundary,
      String name,
      String filename,
      String contentType,
      byte[] content)
      throws IOException {
    out.write(("--" + boundary + "\r\n").getBytes());
    out.write(
        ("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes());
    out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes());
    out.write(content);
    out.write("\r\n".getBytes());
  }
}

package com.splitmanager.api.controller;

import com.splitmanager.api.dto.ParseReceiptResponse;
import com.splitmanager.api.model.ReceiptSession;
import com.splitmanager.api.service.ParsedReceipt;
import com.splitmanager.api.service.ReceiptImageStore;
import com.splitmanager.api.service.ReceiptParsingService;
import com.splitmanager.api.service.ReceiptSessionService;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ReceiptController {

  private final ReceiptSessionService sessionService;
  private final ReceiptParsingService parsingService;
  private final ReceiptImageStore imageStore;
  private final String splitPageBaseUrl;

  public ReceiptController(
      ReceiptSessionService sessionService,
      ReceiptParsingService parsingService,
      ReceiptImageStore imageStore,
      @Value("${split-manager.split-page-base-url:}") String splitPageBaseUrl) {
    this.sessionService = sessionService;
    this.parsingService = parsingService;
    this.imageStore = imageStore;
    this.splitPageBaseUrl = splitPageBaseUrl;
  }

  @PostMapping("/parse-receipt")
  public ResponseEntity<ParseReceiptResponse> parseReceipt(@RequestParam("image") MultipartFile image)
      throws IOException {
    byte[] imageBytes = image.getBytes();
    String contentType = image.getContentType();

    String s3Key = imageStore.upload(imageBytes, contentType);
    ReceiptSession session = sessionService.create(s3Key, contentType);

    try {
      ParsedReceipt parsed = parsingService.parse(imageBytes, contentType);
      sessionService.updateParsedFields(session.getSessionId(), parsed);

      var response =
          new ParseReceiptResponse(
              session.getSessionId(),
              parsed.merchant(),
              parsed.items(),
              parsed.tax(),
              null,
              parsed.total(),
              splitPageBaseUrl + "/split/" + session.getSessionId());
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      sessionService.markParseFailed(session.getSessionId(), e.getMessage());
      throw e;
    }
  }
}

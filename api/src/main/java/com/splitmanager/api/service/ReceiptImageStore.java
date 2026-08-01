package com.splitmanager.api.service;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ReceiptImageStore {

  private final S3Client s3Client;
  private final String bucketName;

  public ReceiptImageStore(S3Client s3Client, @Value("${split-manager.images-bucket-name}") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  public String upload(byte[] imageBytes, String contentType) {
    String key = "receipts/" + UUID.randomUUID();
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(imageBytes));
    return key;
  }

  public byte[] download(String key) {
    try (ResponseInputStream<GetObjectResponse> response =
        s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build())) {
      return response.readAllBytes();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to read receipt image from S3: " + key, e);
    }
  }
}

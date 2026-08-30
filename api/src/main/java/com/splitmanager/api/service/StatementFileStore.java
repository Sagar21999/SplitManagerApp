package com.splitmanager.api.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Holds an uploaded statement only for as long as it takes to parse it.
 *
 * <p>Unlike receipt images, which the user may want to look at again, a statement is a
 * full record of everything the user bought — most of which has nothing to do with
 * splitting. It is deleted as soon as extraction finishes (BRD FR20); the bucket's
 * one-day lifecycle rule is a backstop for the case where the API dies mid-request, not
 * the primary mechanism.
 */
@Service
public class StatementFileStore {

  private static final Logger log = LoggerFactory.getLogger(StatementFileStore.class);

  private final S3Client s3Client;
  private final String bucketName;

  public StatementFileStore(
      S3Client s3Client, @Value("${split-manager.statements-bucket-name}") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  public String upload(byte[] bytes, String contentType) {
    String key = "statements/" + UUID.randomUUID();
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(bytes));
    return key;
  }

  /**
   * Never throws. A failed delete must not turn a successful import into an error the
   * user sees — it is logged, and the lifecycle rule removes the object within the day.
   */
  public void delete(String key) {
    if (key == null) {
      return;
    }
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    } catch (RuntimeException e) {
      log.warn("Failed to delete statement object {}; lifecycle expiry will remove it", key, e);
    }
  }
}

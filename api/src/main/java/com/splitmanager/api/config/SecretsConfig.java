package com.splitmanager.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Slf4j
@Configuration
public class SecretsConfig {

  @Value("${split-manager.splitwise-api-key-secret-name:split-manager/splitwise-api-key}")
  private String secretName;

  // Resolved lazily (not fetched at startup) so the app can still boot without a
  // real secret present, e.g. against DynamoDB Local before Secrets Manager is wired.
  // SplitwiseService surfaces a clear error at call time if this is unavailable.
  @Bean
  public SplitwiseApiKeySupplier splitwiseApiKeySupplier(SecretsManagerClient secretsManagerClient) {
    return () -> {
      try {
        return secretsManagerClient
            .getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build())
            .secretString();
      } catch (Exception e) {
        log.warn("Could not resolve Splitwise API key from Secrets Manager secret '{}': {}", secretName, e.getMessage());
        return null;
      }
    };
  }

  public interface SplitwiseApiKeySupplier {
    String get();
  }
}

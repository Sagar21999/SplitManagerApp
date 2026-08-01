package com.splitmanager.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class AwsClientConfig {

  @Bean
  public DynamoDbClient dynamoDbClient() {
    return DynamoDbClient.create();
  }

  @Bean
  public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
    return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
  }

  @Bean
  public S3Client s3Client() {
    return S3Client.create();
  }

  @Bean
  public TextractClient textractClient() {
    return TextractClient.create();
  }

  @Bean
  public SecretsManagerClient secretsManagerClient() {
    return SecretsManagerClient.create();
  }
}

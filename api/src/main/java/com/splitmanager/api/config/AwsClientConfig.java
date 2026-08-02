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

  // Named awsTextractClient (not textractClient) to avoid colliding with the
  // @Component wrapper class com.splitmanager.api.client.TextractClient, whose
  // auto-generated Spring bean name is also "textractClient".
  @Bean
  public TextractClient awsTextractClient() {
    return TextractClient.create();
  }

  @Bean
  public SecretsManagerClient secretsManagerClient() {
    return SecretsManagerClient.create();
  }
}

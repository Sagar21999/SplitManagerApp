package com.splitmanager.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is now largely vestigial: CloudFront serves the SPA and proxies the API under
 * {@code /api/*} on the same origin, so the browser makes no cross-origin request at all
 * (see infra/lib/constructs/frontend-stack.ts).
 *
 * <p>It is kept configured, and configured narrowly, for the cases that do cross origins
 * — local Vite dev against a deployed API, and any future non-browser client. The v1
 * default of {@code "*"} is gone: a wildcard origin is incompatible with credentialed
 * requests, and would let any page on the internet call this API with a stolen token.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Value("${split-manager.frontend-origin:}")
  private String frontendOrigin;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (frontendOrigin == null || frontendOrigin.isBlank()) {
      // No origin configured means same-origin only. Registering nothing is the correct,
      // most restrictive default — never fall back to a wildcard.
      return;
    }

    registry
        .addMapping("/**")
        .allowedOrigins(frontendOrigin.split(","))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type")
        .allowCredentials(true)
        .maxAge(3600);
  }
}

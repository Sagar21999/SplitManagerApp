package com.splitmanager.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Locks every endpoint behind a valid Cognito access token (BRD FR24/FR25).
 *
 * <p>v1 ran with no authentication at all, which was defensible while sessions were
 * anonymous, transient, and TTL-deleted. v2 stores a permanent transaction ledger and
 * imported bank-statement rows, so an open endpoint would expose the user's complete
 * financial history. There is no unauthenticated route here other than the health check
 * the load balancer needs.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${cognito.issuer-uri:}")
  private String issuerUri;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth
                    // The ALB health check is unauthenticated by necessity — it has no
                    // way to present a token. Exposes only UP/DOWN, no ledger data.
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // CORS preflights never carry an Authorization header. Harmless in
                    // practice since the SPA is same-origin via CloudFront, but a stray
                    // preflight 401 is a confusing failure to debug.
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        // Stateless bearer-token API: no session cookie exists for an attacker to ride,
        // so CSRF protection guards nothing and would reject legitimate POSTs.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults());

    return http.build();
  }

  /**
   * Built explicitly rather than via {@code spring.security.oauth2.resourceserver.jwt
   * .issuer-uri} so that a missing issuer fails loudly at startup with an explanatory
   * message. Fail-closed is the point: the alternative to refusing to start is serving
   * the whole ledger unauthenticated, which is the exact failure this class exists to
   * prevent. Local runs must set COGNITO_ISSUER_URI to a real pool.
   *
   * <p>Audience is deliberately not validated. Cognito puts {@code client_id} (not
   * {@code aud}) on access tokens, and this pool has exactly one client, so issuer
   * validation already establishes that a token came from our app. Audience checking
   * only distinguishes between multiple clients of the same pool.
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    if (issuerUri == null || issuerUri.isBlank()) {
      throw new IllegalStateException(
          "COGNITO_ISSUER_URI is not set. The API refuses to start without it rather than "
              + "silently serving the ledger unauthenticated.");
    }
    return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
  }
}

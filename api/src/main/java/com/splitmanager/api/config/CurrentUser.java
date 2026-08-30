package com.splitmanager.api.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller's user id from the validated JWT.
 *
 * <p>Always the token's {@code sub} claim, never anything from a path, query string, or
 * request body. That is the whole point: a user id the client can influence is a user id
 * the client can forge, and every ledger query is scoped by this value.
 */
@Component
public class CurrentUser {

  public String userId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      // SecurityConfig authenticates every non-health route, so reaching here means the
      // filter chain was misconfigured rather than that the user is merely logged out.
      throw new IllegalStateException("No authenticated JWT on the request.");
    }
    return jwt.getSubject();
  }
}

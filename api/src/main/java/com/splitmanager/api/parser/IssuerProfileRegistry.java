package com.splitmanager.api.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads {@code issuer-profiles.yml} once at startup (LLD 6.1).
 *
 * <p>Read straight from the classpath rather than bound through {@code @ConfigurationProperties}
 * so the profiles stay in their own file: they are reference data that changes when an
 * issuer changes its export format, not application configuration.
 */
@Component
public class IssuerProfileRegistry {

  private static final String RESOURCE = "issuer-profiles.yml";

  private final Map<String, IssuerProfile> byId = new LinkedHashMap<>();

  public IssuerProfileRegistry() {
    this(RESOURCE);
  }

  IssuerProfileRegistry(String resourceName) {
    load(resourceName);
  }

  @SuppressWarnings("unchecked")
  private void load(String resourceName) {
    ClassPathResource resource = new ClassPathResource(resourceName);
    if (!resource.exists()) {
      // Not fatal: without profiles the parser falls back to header inference, which
      // handles most exports. Failing startup over reference data would be worse.
      return;
    }
    try (InputStream in = resource.getInputStream()) {
      Map<String, Object> root = new Yaml().load(in);
      if (root == null || root.get("profiles") == null) {
        return;
      }
      for (Map<String, Object> entry : (List<Map<String, Object>>) root.get("profiles")) {
        IssuerProfile profile =
            new IssuerProfile(
                string(entry, "id"),
                string(entry, "label"),
                string(entry, "dateColumn"),
                string(entry, "descriptionColumn"),
                string(entry, "amountColumn"),
                string(entry, "debitCreditColumn"),
                string(entry, "dateFormat"),
                Boolean.TRUE.equals(entry.get("debitsArePositive")));
        byId.put(profile.id(), profile);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read " + resourceName, e);
    }
  }

  private static String string(Map<String, Object> entry, String key) {
    Object value = entry.get(key);
    return value == null ? null : value.toString();
  }

  public Optional<IssuerProfile> find(String id) {
    return id == null || id.isBlank() ? Optional.empty() : Optional.ofNullable(byId.get(id));
  }

  public List<IssuerProfile> all() {
    return new ArrayList<>(byId.values());
  }
}

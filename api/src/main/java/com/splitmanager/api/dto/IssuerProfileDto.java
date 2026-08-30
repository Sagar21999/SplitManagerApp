package com.splitmanager.api.dto;

import com.splitmanager.api.parser.IssuerProfile;

/** Just enough to populate the issuer picker; the column mapping stays server-side. */
public record IssuerProfileDto(String id, String label) {

  public static IssuerProfileDto from(IssuerProfile profile) {
    return new IssuerProfileDto(profile.id(), profile.label());
  }
}

package com.splitmanager.api.dto;

import com.splitmanager.api.model.StatementImport;
import com.splitmanager.api.model.StatementImportStatus;
import java.time.Instant;
import java.util.List;

/**
 * An import and everything it produced.
 *
 * <p>Candidates are returned inline on upload so the review page has what it needs
 * without a second round trip.
 */
public record StatementImportDto(
    String importId,
    String fileName,
    String issuerProfile,
    Instant uploadedAt,
    int rowCount,
    int candidateCount,
    StatementImportStatus status,
    String failureReason,
    List<CandidateDto> candidates) {

  public static StatementImportDto from(StatementImport i, List<CandidateDto> candidates) {
    return new StatementImportDto(
        i.getImportId(),
        i.getFileName(),
        i.getIssuerProfile(),
        i.getUploadedAt(),
        i.getRowCount(),
        i.getCandidateCount(),
        i.getStatus(),
        i.getFailureReason(),
        candidates);
  }
}

package com.splitmanager.api.dto;

import com.splitmanager.api.model.CandidateClassification;
import com.splitmanager.api.model.CandidateStatus;
import com.splitmanager.api.model.DuplicateMatch;
import com.splitmanager.api.model.StatementCandidate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Wire shape of a statement row awaiting review. */
public record CandidateDto(
    String candidateId,
    String importId,
    int sequence,
    LocalDate date,
    String rawDescription,
    String normalizedMerchant,
    BigDecimal amount,
    CandidateClassification classification,
    BigDecimal classificationConfidence,
    List<DuplicateMatch> duplicateMatches,
    CandidateStatus status,
    String resultingTransactionId) {

  public static CandidateDto from(StatementCandidate c) {
    return new CandidateDto(
        c.getCandidateId(),
        c.getImportId(),
        c.getSequence(),
        c.getDate(),
        c.getRawDescription(),
        c.getNormalizedMerchant(),
        c.getAmount(),
        c.getClassification(),
        c.getClassificationConfidence(),
        c.getDuplicateMatches() == null ? List.of() : c.getDuplicateMatches(),
        c.getStatus(),
        c.getResultingTransactionId());
  }
}

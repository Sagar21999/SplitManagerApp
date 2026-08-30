package com.splitmanager.api.service;

import com.splitmanager.api.exception.StatementCandidateNotFoundException;
import com.splitmanager.api.exception.StatementImportNotFoundException;
import com.splitmanager.api.exception.StatementParseException;
import com.splitmanager.api.exception.ValidationException;
import com.splitmanager.api.model.CandidateClassification;
import com.splitmanager.api.model.CandidateStatus;
import com.splitmanager.api.model.DuplicateMatch;
import com.splitmanager.api.model.StatementCandidate;
import com.splitmanager.api.model.StatementImport;
import com.splitmanager.api.model.StatementImportStatus;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionStatus;
import com.splitmanager.api.model.TransactionType;
import com.splitmanager.api.parser.ParseResult;
import com.splitmanager.api.parser.RawStatementRow;
import com.splitmanager.api.parser.StatementParser;
import com.splitmanager.api.repository.StatementImportRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Turns an uploaded statement into a reviewable list of candidates (LLD 3.3).
 *
 * <p>Store, parse, classify, dedup-check, persist, delete the file. Nothing here writes
 * to the ledger — a candidate becomes a transaction only when the user confirms it, in
 * {@link #confirm}.
 */
@Service
public class StatementIngestionService {

  private final List<StatementParser> parsers;
  private final StatementFileStore fileStore;
  private final StatementImportRepository importRepository;
  private final StatementClassificationService classificationService;
  private final DeduplicationService deduplicationService;
  private final TransactionService transactionService;

  public StatementIngestionService(
      List<StatementParser> parsers,
      StatementFileStore fileStore,
      StatementImportRepository importRepository,
      StatementClassificationService classificationService,
      DeduplicationService deduplicationService,
      TransactionService transactionService) {
    this.parsers = parsers;
    this.fileStore = fileStore;
    this.importRepository = importRepository;
    this.classificationService = classificationService;
    this.deduplicationService = deduplicationService;
    this.transactionService = transactionService;
  }

  public Ingested ingest(
      String userId, byte[] bytes, String fileName, String contentType, String issuerProfileId) {
    if (bytes == null || bytes.length == 0) {
      throw new ValidationException("The uploaded statement file is empty.");
    }

    StatementParser parser =
        parsers.stream()
            .filter(p -> p.supports(contentType, fileName))
            .findFirst()
            .orElseThrow(
                () ->
                    new StatementParseException(
                        "No parser for this file type. Upload a CSV export from your card issuer."));

    StatementImport statementImport =
        StatementImport.builder()
            .importId(UUID.randomUUID().toString())
            .userId(userId)
            .fileName(fileName)
            .contentType(contentType)
            .issuerProfile(issuerProfileId)
            .uploadedAt(Instant.now())
            .status(StatementImportStatus.PARSING)
            .build();

    // The object is written before parsing and deleted after, whatever happens. Holding
    // it only for the duration of the parse is the whole point of the bucket.
    String s3Key = fileStore.upload(bytes, contentType);
    try {
      ParseResult parsed = parser.parse(bytes, issuerProfileId);
      List<StatementCandidate> candidates = buildCandidates(userId, statementImport.getImportId(), parsed);

      statementImport.setRowCount(parsed.totalRows());
      statementImport.setCandidateCount(candidates.size());
      statementImport.setStatus(StatementImportStatus.READY);
      statementImport.setFailureReason(partialParseNote(parsed));

      importRepository.saveImport(statementImport);
      if (!candidates.isEmpty()) {
        importRepository.saveCandidates(candidates);
      }
      return new Ingested(statementImport, candidates);

    } catch (RuntimeException e) {
      // The import is recorded even when it fails, so the user sees why rather than
      // getting an error toast and no trace of the upload.
      statementImport.setStatus(StatementImportStatus.FAILED);
      statementImport.setFailureReason(e.getMessage());
      importRepository.saveImport(statementImport);
      throw e;
    } finally {
      fileStore.delete(s3Key);
    }
  }

  private List<StatementCandidate> buildCandidates(String userId, String importId, ParseResult parsed) {
    // Both lookups are per-import, not per-row: the history map is built once, and the
    // dedup queries are the only per-row cost.
    Map<String, TransactionType> history =
        classificationService.buildHistory(transactionService.list(userId, null, null, Integer.MAX_VALUE));

    List<StatementCandidate> candidates = new ArrayList<>();
    int sequence = 0;
    for (RawStatementRow row : parsed.rows()) {
      StatementClassificationService.Classification classification =
          classificationService.classify(row, history);

      List<DuplicateMatch> duplicates =
          deduplicationService.findMatches(userId, row.description(), row.date(), row.amount());

      candidates.add(
          StatementCandidate.builder()
              .candidateId(UUID.randomUUID().toString())
              .importId(importId)
              .sequence(sequence++)
              .date(row.date())
              .rawDescription(row.description())
              .normalizedMerchant(Transaction.normalizeMerchant(row.description()))
              .amount(row.amount())
              .classification(classification.classification())
              .classificationConfidence(classification.confidence())
              .duplicateMatches(duplicates)
              .status(CandidateStatus.PENDING)
              .build());
    }
    return candidates;
  }

  /** Null when everything parsed; otherwise what was dropped and why. */
  private String partialParseNote(ParseResult parsed) {
    if (parsed.droppedRows() == 0) {
      return null;
    }
    return parsed.droppedRows()
        + " of "
        + parsed.totalRows()
        + " rows could not be read and were skipped.";
  }

  public StatementImport getImport(String userId, String importId) {
    StatementImport statementImport =
        importRepository
            .findImport(importId)
            .orElseThrow(() -> new StatementImportNotFoundException(importId));
    // Same reasoning as TransactionService.get: the id is client-supplied and guessable,
    // so ownership is verified rather than assumed, and a mismatch reads as not-found.
    if (!statementImport.getUserId().equals(userId)) {
      throw new StatementImportNotFoundException(importId);
    }
    return statementImport;
  }

  public List<StatementCandidate> listCandidates(String userId, String importId) {
    getImport(userId, importId);
    return importRepository.findCandidates(importId);
  }

  /**
   * Promotes a candidate into the ledger.
   *
   * <p>A reimbursement has nothing left to decide — the whole amount is claimed from the
   * employer — so it is opened immediately. A split still needs participants and a mode,
   * so it lands as a DRAFT for the split editor to finish.
   */
  public Transaction confirm(
      String userId,
      String importId,
      String candidateId,
      TransactionType type,
      String merchant,
      LocalDate date,
      BigDecimal amount) {
    getImport(userId, importId);
    StatementCandidate candidate =
        importRepository
            .findCandidate(importId, candidateId)
            .orElseThrow(() -> new StatementCandidateNotFoundException(candidateId));

    if (candidate.getStatus() == CandidateStatus.CONFIRMED) {
      throw new ValidationException("This statement row has already been added to the ledger.");
    }

    TransactionType resolvedType =
        type != null
            ? type
            : candidate.getClassification() == CandidateClassification.LIKELY_REIMBURSEMENT
                ? TransactionType.REIMBURSEMENT
                : TransactionType.SPLIT;

    Transaction transaction =
        transactionService.create(
            userId,
            resolvedType,
            merchant != null && !merchant.isBlank() ? merchant.trim() : candidate.getRawDescription(),
            date != null ? date : candidate.getDate(),
            amount != null ? amount : candidate.getAmount(),
            importId);

    if (resolvedType == TransactionType.REIMBURSEMENT) {
      transaction =
          transactionService.updateStatus(userId, transaction.getTransactionId(), TransactionStatus.OPEN);
    }

    candidate.setStatus(CandidateStatus.CONFIRMED);
    candidate.setResultingTransactionId(transaction.getTransactionId());
    importRepository.updateCandidate(candidate);

    return transaction;
  }

  public void dismiss(String userId, String importId, String candidateId) {
    getImport(userId, importId);
    StatementCandidate candidate =
        importRepository
            .findCandidate(importId, candidateId)
            .orElseThrow(() -> new StatementCandidateNotFoundException(candidateId));
    candidate.setStatus(CandidateStatus.DISMISSED);
    importRepository.updateCandidate(candidate);
  }

  public record Ingested(StatementImport statementImport, List<StatementCandidate> candidates) {}
}

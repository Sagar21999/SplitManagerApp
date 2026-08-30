package com.splitmanager.api.controller;

import com.splitmanager.api.config.CurrentUser;
import com.splitmanager.api.dto.CandidateDto;
import com.splitmanager.api.dto.ConfirmCandidateRequest;
import com.splitmanager.api.dto.IssuerProfileDto;
import com.splitmanager.api.dto.StatementImportDto;
import com.splitmanager.api.dto.TransactionDto;
import com.splitmanager.api.model.StatementCandidate;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.parser.IssuerProfileRegistry;
import com.splitmanager.api.service.StatementIngestionService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Statement upload and candidate review (LLD 3.2, 4.4). */
@RestController
@RequestMapping("/statements")
public class StatementController {

  private final StatementIngestionService ingestionService;
  private final IssuerProfileRegistry issuerProfiles;
  private final CurrentUser currentUser;

  public StatementController(
      StatementIngestionService ingestionService,
      IssuerProfileRegistry issuerProfiles,
      CurrentUser currentUser) {
    this.ingestionService = ingestionService;
    this.issuerProfiles = issuerProfiles;
    this.currentUser = currentUser;
  }

  /** The issuers with a known column mapping, for the import page's picker. */
  @GetMapping("/issuer-profiles")
  public ResponseEntity<List<IssuerProfileDto>> issuerProfiles() {
    return ResponseEntity.ok(issuerProfiles.all().stream().map(IssuerProfileDto::from).toList());
  }

  /**
   * Upload and extract in one request. Parsing a CSV is fast enough to be synchronous;
   * the PARSING status exists for the PDF path that Phase 5 adds.
   */
  @PostMapping
  public ResponseEntity<StatementImportDto> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) String issuerProfile)
      throws IOException {
    StatementIngestionService.Ingested ingested =
        ingestionService.ingest(
            currentUser.userId(),
            file.getBytes(),
            file.getOriginalFilename(),
            file.getContentType(),
            issuerProfile);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(StatementImportDto.from(ingested.statementImport(), toDtos(ingested.candidates())));
  }

  @GetMapping("/{id}")
  public ResponseEntity<StatementImportDto> get(@PathVariable String id) {
    String userId = currentUser.userId();
    return ResponseEntity.ok(
        StatementImportDto.from(
            ingestionService.getImport(userId, id),
            toDtos(ingestionService.listCandidates(userId, id))));
  }

  @GetMapping("/{id}/candidates")
  public ResponseEntity<List<CandidateDto>> candidates(@PathVariable String id) {
    return ResponseEntity.ok(toDtos(ingestionService.listCandidates(currentUser.userId(), id)));
  }

  @PostMapping("/{id}/candidates/{candidateId}/confirm")
  public ResponseEntity<TransactionDto> confirm(
      @PathVariable String id,
      @PathVariable String candidateId,
      @RequestBody(required = false) ConfirmCandidateRequest request) {
    ConfirmCandidateRequest edits =
        request == null ? new ConfirmCandidateRequest(null, null, null, null) : request;

    Transaction transaction =
        ingestionService.confirm(
            currentUser.userId(),
            id,
            candidateId,
            edits.type(),
            edits.merchant(),
            edits.date(),
            edits.amount());
    return ResponseEntity.status(HttpStatus.CREATED).body(TransactionDto.from(transaction));
  }

  @PostMapping("/{id}/candidates/{candidateId}/dismiss")
  public ResponseEntity<Void> dismiss(@PathVariable String id, @PathVariable String candidateId) {
    ingestionService.dismiss(currentUser.userId(), id, candidateId);
    return ResponseEntity.noContent().build();
  }

  private static List<CandidateDto> toDtos(List<StatementCandidate> candidates) {
    return candidates.stream().map(CandidateDto::from).toList();
  }
}

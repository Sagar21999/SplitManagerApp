package com.splitmanager.api.controller;

import com.splitmanager.api.config.CurrentUser;
import com.splitmanager.api.dto.CreateTransactionRequest;
import com.splitmanager.api.dto.FinalizeRequest;
import com.splitmanager.api.dto.ReceiptDraftDto;
import com.splitmanager.api.dto.StatusUpdateRequest;
import com.splitmanager.api.dto.TransactionDetailDto;
import com.splitmanager.api.dto.TransactionDto;
import com.splitmanager.api.dto.UpdateTransactionRequest;
import com.splitmanager.api.model.Person;
import com.splitmanager.api.model.SplitDefinition;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionStatus;
import com.splitmanager.api.model.TransactionType;
import com.splitmanager.api.service.DeduplicationService;
import com.splitmanager.api.service.PersonService;
import com.splitmanager.api.service.ReceiptImageStore;
import com.splitmanager.api.service.ReceiptParsingService;
import com.splitmanager.api.service.ParsedReceipt;
import com.splitmanager.api.service.SplitSummaryService;
import com.splitmanager.api.service.TransactionService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

  private final TransactionService transactionService;
  private final PersonService personService;
  private final SplitSummaryService summaryService;
  private final ReceiptParsingService parsingService;
  private final ReceiptImageStore imageStore;
  private final DeduplicationService deduplicationService;
  private final CurrentUser currentUser;

  public TransactionController(
      TransactionService transactionService,
      PersonService personService,
      SplitSummaryService summaryService,
      ReceiptParsingService parsingService,
      ReceiptImageStore imageStore,
      DeduplicationService deduplicationService,
      CurrentUser currentUser) {
    this.transactionService = transactionService;
    this.personService = personService;
    this.summaryService = summaryService;
    this.parsingService = parsingService;
    this.imageStore = imageStore;
    this.deduplicationService = deduplicationService;
    this.currentUser = currentUser;
  }

  /**
   * Upload a receipt photo; get back a draft transaction with the parsed fields, plus a
   * warning if the ledger already holds something that looks like the same charge.
   */
  @PostMapping("/from-receipt")
  public ResponseEntity<ReceiptDraftDto> createFromReceipt(@RequestParam("image") MultipartFile image)
      throws IOException {
    String userId = currentUser.userId();
    byte[] bytes = image.getBytes();
    String contentType = image.getContentType();

    String s3Key = imageStore.upload(bytes, contentType);
    ParsedReceipt parsed = parsingService.parse(bytes, contentType);

    BigDecimal subtotal =
        parsed.items() == null
            ? null
            : parsed.items().stream().map(i -> i.getPrice()).reduce(BigDecimal.ZERO, BigDecimal::add);
    // Textract does not always find a TOTAL. Falling back to the line-item subtotal keeps
    // the draft usable; the user corrects it during review either way.
    BigDecimal total = parsed.total() != null ? parsed.total() : subtotal;

    Transaction transaction =
        transactionService.createFromReceipt(
            userId, s3Key, parsed.merchant(), LocalDate.now(), parsed.items(), subtotal, parsed.tax(), total);

    // Checked after the draft exists, so the draft itself is excluded from the results -
    // it shares the merchant, date, and amount it was just created with.
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new ReceiptDraftDto(
                TransactionDto.from(transaction),
                deduplicationService.findMatches(
                    userId,
                    transaction.getMerchant(),
                    transaction.getTransactionDate(),
                    transaction.getTotal(),
                    transaction.getTransactionId())));
  }

  @PostMapping
  public ResponseEntity<TransactionDto> create(@RequestBody CreateTransactionRequest request) {
    Transaction transaction =
        transactionService.create(
            currentUser.userId(),
            request.type() == null ? TransactionType.SPLIT : request.type(),
            request.merchant(),
            request.transactionDate(),
            request.total(),
            request.sourceStatementImportId());
    return ResponseEntity.status(HttpStatus.CREATED).body(TransactionDto.from(transaction));
  }

  @GetMapping
  public ResponseEntity<List<TransactionDto>> list(
      @RequestParam(required = false) TransactionStatus status,
      @RequestParam(required = false) TransactionType type,
      @RequestParam(defaultValue = "50") int limit) {
    List<TransactionDto> transactions =
        transactionService.list(currentUser.userId(), status, type, limit).stream()
            .map(TransactionDto::from)
            .toList();
    return ResponseEntity.ok(transactions);
  }

  @GetMapping("/{id}")
  public ResponseEntity<TransactionDetailDto> get(@PathVariable String id) {
    String userId = currentUser.userId();
    Transaction transaction = transactionService.get(userId, id);
    return ResponseEntity.ok(
        new TransactionDetailDto(
            TransactionDto.from(transaction),
            summaryService.generateSummary(transaction, personNames(userId))));
  }

  @PutMapping("/{id}")
  public ResponseEntity<TransactionDto> update(
      @PathVariable String id, @RequestBody UpdateTransactionRequest request) {
    Transaction transaction =
        transactionService.updateDraft(
            currentUser.userId(),
            id,
            request.merchant(),
            request.transactionDate(),
            request.items(),
            request.subtotal(),
            request.tax(),
            request.tip(),
            request.total(),
            request.notes());
    return ResponseEntity.ok(TransactionDto.from(transaction));
  }

  @PostMapping("/{id}/finalize")
  public ResponseEntity<TransactionDetailDto> finalizeSplit(
      @PathVariable String id, @RequestBody FinalizeRequest request) {
    String userId = currentUser.userId();

    // Names typed on this transaction become directory entries before the split runs, so
    // the ids referenced by the definition are guaranteed to resolve afterwards.
    List<Person> created = personService.resolveOrCreate(userId, request.newPersonNames());

    SplitDefinition definition = request.split();
    if (definition != null && definition.getParticipantIds() != null && !created.isEmpty()) {
      // Substitute any raw name still sitting in the participant list for its new id.
      Map<String, String> byName = new HashMap<>();
      created.forEach(p -> byName.put(p.getDisplayName(), p.getPersonId()));
      definition.setParticipantIds(
          definition.getParticipantIds().stream().map(v -> byName.getOrDefault(v, v)).toList());
    }

    Transaction transaction = transactionService.finalizeSplit(userId, id, definition, request.tip());
    return ResponseEntity.ok(
        new TransactionDetailDto(
            TransactionDto.from(transaction),
            summaryService.generateSummary(transaction, personNames(userId))));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<TransactionDto> updateStatus(
      @PathVariable String id, @RequestBody StatusUpdateRequest request) {
    Transaction transaction =
        transactionService.updateStatus(currentUser.userId(), id, request.status());
    return ResponseEntity.ok(TransactionDto.from(transaction));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    transactionService.delete(currentUser.userId(), id);
    return ResponseEntity.noContent().build();
  }

  private Map<String, String> personNames(String userId) {
    Map<String, String> names = new HashMap<>();
    personService.listIncludingArchived(userId).forEach(p -> names.put(p.getPersonId(), p.getDisplayName()));
    return names;
  }
}

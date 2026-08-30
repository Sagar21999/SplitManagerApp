package com.splitmanager.api.parser;

/**
 * Extracts debit rows from an uploaded statement (LLD 3.4).
 *
 * <p>The interface exists so the PDF path (Phase 5, Textract plus Bedrock) can slot in
 * behind the same ingestion pipeline as the deterministic CSV path.
 */
public interface StatementParser {

  boolean supports(String contentType, String fileName);

  ParseResult parse(byte[] bytes, String issuerProfileId);
}

package com.splitmanager.api.model;

/**
 * Lifecycle of a statement upload.
 *
 * <p>PARSING exists for the PDF path (Phase 5), where extraction is slow enough to be
 * worth showing. CSV imports go straight to READY or FAILED inside the request.
 */
public enum StatementImportStatus {
  PARSING,
  READY,
  FAILED
}

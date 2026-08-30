package com.splitmanager.api.dto;

import com.splitmanager.api.model.DuplicateMatch;
import java.util.List;

/**
 * A freshly parsed receipt plus any transaction that looks like the same charge.
 *
 * <p>The warning belongs on this path as much as on the statement path (BRD FR19): the
 * common way to double-count is photographing a receipt for something already imported
 * from a statement.
 */
public record ReceiptDraftDto(TransactionDto transaction, List<DuplicateMatch> duplicateWarnings) {}

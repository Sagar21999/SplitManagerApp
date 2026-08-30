package com.splitmanager.api.dto;

import com.splitmanager.api.model.SplitDefinition;
import java.math.BigDecimal;
import java.util.List;

/**
 * @param newPersonNames free-text names typed on this transaction that are not yet in the
 *     directory. Resolved to person ids and saved before the split is computed (BRD FR13).
 */
public record FinalizeRequest(
    SplitDefinition split, BigDecimal tip, List<String> newPersonNames) {}

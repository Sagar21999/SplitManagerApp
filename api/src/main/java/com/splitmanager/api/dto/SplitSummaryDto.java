package com.splitmanager.api.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param amountOwedByParticipant participantId to amount
 * @param shareText copy-pasteable plain text for manual handoff to Splitwise
 */
public record SplitSummaryDto(
    Map<String, BigDecimal> amountOwedByParticipant, String shareText) {}

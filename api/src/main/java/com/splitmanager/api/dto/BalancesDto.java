package com.splitmanager.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** BRD FR12. A positive netAmount means that person owes the user. */
public record BalancesDto(List<PersonBalanceDto> balances, BigDecimal totalOwedToUser) {

  public record PersonBalanceDto(
      String personId, String displayName, BigDecimal netAmount, int openTransactionCount) {}
}

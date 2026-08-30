package com.splitmanager.api.controller;

import com.splitmanager.api.config.CurrentUser;
import com.splitmanager.api.dto.BalancesDto;
import com.splitmanager.api.service.BalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** BRD FR12 - the "who owes me what right now?" endpoint. */
@RestController
public class BalanceController {

  private final BalanceService balanceService;
  private final CurrentUser currentUser;

  public BalanceController(BalanceService balanceService, CurrentUser currentUser) {
    this.balanceService = balanceService;
    this.currentUser = currentUser;
  }

  @GetMapping("/balances")
  public ResponseEntity<BalancesDto> balances() {
    BalanceService.Balances balances = balanceService.computeBalances(currentUser.userId());
    return ResponseEntity.ok(
        new BalancesDto(
            balances.balances().stream()
                .map(
                    b ->
                        new BalancesDto.PersonBalanceDto(
                            b.personId(), b.displayName(), b.netAmount(), b.openTransactionCount()))
                .toList(),
            balances.totalOwedToUser()));
  }
}

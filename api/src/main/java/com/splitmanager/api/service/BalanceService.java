package com.splitmanager.api.service;

import com.splitmanager.api.model.FinalizedSplit;
import com.splitmanager.api.model.Participants;
import com.splitmanager.api.model.Person;
import com.splitmanager.api.model.Transaction;
import com.splitmanager.api.model.TransactionType;
import com.splitmanager.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Per-person net balances (BRD FR12) — the "who owes me what right now?" answer that
 * replaces opening Splitwise.
 *
 * <p>Computed on read by aggregating transactions, rather than maintained as running
 * counters. Counters would need updating on every status change and every edit, and drift
 * between a counter and its underlying rows is both easy to introduce and very hard to
 * notice in a money app. At a few hundred transactions a year the aggregation is cheap.
 * If that ever stops being true, the migration is a materialized balance row per person.
 */
@Service
public class BalanceService {

  private final TransactionRepository transactionRepository;
  private final PersonService personService;

  public BalanceService(TransactionRepository transactionRepository, PersonService personService) {
    this.transactionRepository = transactionRepository;
    this.personService = personService;
  }

  public record PersonBalance(
      String personId, String displayName, BigDecimal netAmount, int openTransactionCount) {}

  public record Balances(List<PersonBalance> balances, BigDecimal totalOwedToUser) {}

  public Balances computeBalances(String userId) {
    Map<String, BigDecimal> net = new LinkedHashMap<>();
    Map<String, Integer> counts = new HashMap<>();

    for (Transaction transaction : transactionRepository.listAll(userId)) {
      // Reimbursements are owed by an employer, not a person in the directory, so they
      // are deliberately absent from these balances.
      if (transaction.getType() == TransactionType.REIMBURSEMENT) {
        continue;
      }
      if (!transaction.getStatus().countsTowardBalance()) {
        continue;
      }
      FinalizedSplit split = transaction.getFinalizedSplit();
      if (split == null || split.getParticipantShares() == null) {
        continue;
      }

      String payerId = split.getPayerId();
      for (Map.Entry<String, BigDecimal> entry : split.getParticipantShares().entrySet()) {
        String participantId = entry.getKey();
        BigDecimal share = entry.getValue();

        if (Participants.SELF.equals(payerId)) {
          // The user fronted it, so everyone else's share is owed to them.
          if (!Participants.SELF.equals(participantId)) {
            net.merge(participantId, share, BigDecimal::add);
            counts.merge(participantId, 1, Integer::sum);
          }
        } else if (Participants.SELF.equals(participantId)) {
          // Someone else fronted it and the user consumed a share, so the user owes
          // the payer — a negative balance against that person.
          net.merge(payerId, share.negate(), BigDecimal::add);
          counts.merge(payerId, 1, Integer::sum);
        }
        // Shares between two other people are not the user's concern: this ledger
        // tracks the user's own position, not a general multi-party graph.
      }
    }

    Map<String, String> names = new HashMap<>();
    for (Person person : personService.listIncludingArchived(userId)) {
      names.put(person.getPersonId(), person.getDisplayName());
    }

    List<PersonBalance> balances =
        net.entrySet().stream()
            .filter(e -> e.getValue().signum() != 0)
            .map(
                e ->
                    new PersonBalance(
                        e.getKey(),
                        names.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue(),
                        counts.getOrDefault(e.getKey(), 0)))
            .sorted((a, b) -> b.netAmount().compareTo(a.netAmount()))
            .toList();

    BigDecimal total =
        balances.stream().map(PersonBalance::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new Balances(balances, total);
  }
}

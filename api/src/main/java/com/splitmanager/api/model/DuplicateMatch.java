package com.splitmanager.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * An existing transaction that may be the same charge as a statement row (LLD 7).
 *
 * <p>Carries the matched transaction's merchant and date alongside the score because the
 * warning is shown to a person: "possible duplicate of <id>" is not something anyone can
 * act on, whereas "Blue Bottle Coffee, Aug 12" is.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class DuplicateMatch {

  /** {@code MERCHANT_DATE_AMOUNT} (strong) or {@code DATE_AMOUNT} (weak). */
  public static final String MERCHANT_DATE_AMOUNT = "MERCHANT_DATE_AMOUNT";

  public static final String DATE_AMOUNT = "DATE_AMOUNT";

  private String transactionId;
  private String matchStrategy;
  private BigDecimal score;

  private String merchant;
  private LocalDate transactionDate;
}

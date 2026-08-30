package com.splitmanager.api.model;

/**
 * How likely a statement row is to be something the user acts on (LLD 6.3).
 *
 * <p>Deliberately conservative: a noisy candidate list stops being reviewed, so precision
 * matters more than recall. UNLIKELY rows are kept rather than dropped — they are just
 * collapsed in the UI.
 */
public enum CandidateClassification {
  LIKELY_SPLIT,
  LIKELY_REIMBURSEMENT,
  UNLIKELY
}

package io.casehub.agency.deliberation.judgment;

public enum VerifierFailurePolicy {
    RETRY_WITH_FEEDBACK,
    ESCALATE,
    FAIL
}

package io.casehub.agency.deliberation.judgment;

public sealed interface AgreementResult {

    record Agreed(JudgmentResponse selectedResponse) implements AgreementResult {}

    record Disagreed(String reason) implements AgreementResult {}
}

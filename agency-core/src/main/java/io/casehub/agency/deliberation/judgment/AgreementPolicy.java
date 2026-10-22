package io.casehub.agency.deliberation.judgment;

import java.util.List;

@FunctionalInterface
public interface AgreementPolicy {

    AgreementResult evaluate(List<JudgmentResponse> responses);
}

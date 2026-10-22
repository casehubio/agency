package io.casehub.agency.agentic.judgment;

import java.util.List;

@FunctionalInterface
public interface AgreementPolicy {

    AgreementResult evaluate(List<JudgmentResponse> responses);
}

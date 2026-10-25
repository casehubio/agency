package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.core.invoke.StructuredAgentInvoker;

import java.util.List;

public record ActionResult(
    List<RecommendedAction> recommendedActions,
    double confidence,
    String reasoning,
    StructuredAgentInvoker.InvocationMetadata invocationMetadata) {

    public ActionResult {
        recommendedActions = List.copyOf(recommendedActions);
    }
}

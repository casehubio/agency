package io.casehub.agency.stigmergy.llm;

import java.util.List;

public record LlmActionResponse(List<LlmRecommendation> actions) {

    public LlmActionResponse {
        actions = List.copyOf(actions);
    }
}

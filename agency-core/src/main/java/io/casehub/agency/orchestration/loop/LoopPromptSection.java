package io.casehub.agency.orchestration.loop;

@FunctionalInterface
public interface LoopPromptSection {
    String contribute(DecisionContext context);
}

package io.casehub.agency.optimisation.prompt;

@FunctionalInterface
public interface SystemPromptCustomiser {
    String customise(String baseSystemPrompt, String signatureId, String variantSlot);
}

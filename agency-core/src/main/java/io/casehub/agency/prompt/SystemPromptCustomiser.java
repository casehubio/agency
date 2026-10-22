package io.casehub.agency.prompt;

@FunctionalInterface
public interface SystemPromptCustomiser {
    String customise(String baseSystemPrompt, String signatureId, String variantSlot);
}

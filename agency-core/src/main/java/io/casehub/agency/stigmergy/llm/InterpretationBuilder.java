package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.perception.observation.ObservationRenderer;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.platform.agent.AgentProvider;

import java.util.concurrent.ExecutorService;

public final class InterpretationBuilder<E> {

    private static final LlmInvocationPolicy DEFAULT_POLICY = LlmInvocationPolicy.of(
            java.time.Duration.ofMinutes(1), java.time.Duration.ofMinutes(5), java.time.Duration.ofMinutes(30));

    private final AgentProvider          agentProvider;
    private       String                 systemPrompt;
    private       LlmInvocationPolicy    invocationPolicy;
    private       ObservationRenderer<E> promptRenderer;
    private       String                 model;
    private       java.util.Set<String>  watchedKeys;
    private       ExecutorService        llmExecutor;

    private InterpretationBuilder(AgentProvider agentProvider) {
        this.agentProvider = java.util.Objects.requireNonNull(agentProvider, "agentProvider");
    }

    public static <E> InterpretationBuilder<E> builder(AgentProvider agentProvider) {
        return new InterpretationBuilder<>(agentProvider);
    }

    public InterpretationBuilder<E> systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public InterpretationBuilder<E> invocationPolicy(LlmInvocationPolicy policy) {
        this.invocationPolicy = policy;
        return this;
    }

    public InterpretationBuilder<E> promptRenderer(ObservationRenderer<E> renderer) {
        this.promptRenderer = renderer;
        return this;
    }

    public InterpretationBuilder<E> model(String modelId) {
        this.model = modelId;
        return this;
    }

    public InterpretationBuilder<E> watchedKeys(java.util.Set<String> keys) {
        this.watchedKeys = keys;
        return this;
    }

    public InterpretationBuilder<E> llmExecutor(ExecutorService executor) {
        this.llmExecutor = executor;
        return this;
    }

    public ContentSummariser<E, LlmObservationResult> buildObservation() {
        validate();
        return LlmSummarisers.observation(agentProvider, systemPrompt,
                                          resolvePolicy(), promptRenderer, llmExecutor);
    }

    public ContentSummariser<E, InterpretationResult> buildCombined() {
        validate();
        return LlmSummarisers.combined(agentProvider, systemPrompt,
                                       resolvePolicy(), promptRenderer, llmExecutor);
    }

    public String model() {
        return model;
    }

    public java.util.Set<String> watchedKeys() {
        return watchedKeys;
    }

    private void validate() {
        if (systemPrompt == null) {
            throw new IllegalStateException("systemPrompt is required");
        }
        if (promptRenderer == null) {
            throw new IllegalStateException("promptRenderer is required");
        }
        if (llmExecutor == null) {
            throw new IllegalStateException("llmExecutor is required");
        }
    }

    private LlmInvocationPolicy resolvePolicy() {
        return invocationPolicy != null ? invocationPolicy : DEFAULT_POLICY;
    }
}

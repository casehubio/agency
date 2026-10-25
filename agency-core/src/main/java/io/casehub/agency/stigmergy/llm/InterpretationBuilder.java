package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.perception.observation.ObservationRenderer;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.platform.agent.AgentProvider;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public final class InterpretationBuilder<E> {

    private final AgentProvider agentProvider;
    private final String systemPrompt;
    private final LlmInvocationPolicy policy;
    private final ObservationRenderer<E> renderer;
    private final ExecutorService llmExecutor;
    private String actionSystemPrompt;
    private Supplier<String> contextSupplier = () -> "";

    private InterpretationBuilder(AgentProvider agentProvider,
                                   String systemPrompt,
                                   LlmInvocationPolicy policy,
                                   ObservationRenderer<E> renderer,
                                   ExecutorService llmExecutor) {
        this.agentProvider = agentProvider;
        this.systemPrompt = systemPrompt;
        this.policy = policy;
        this.renderer = renderer;
        this.llmExecutor = llmExecutor;
    }

    public static <E> InterpretationBuilder<E> create(AgentProvider agentProvider,
                                                        String systemPrompt,
                                                        LlmInvocationPolicy policy,
                                                        ObservationRenderer<E> renderer,
                                                        ExecutorService llmExecutor) {
        return new InterpretationBuilder<>(agentProvider, systemPrompt, policy,
            renderer, llmExecutor);
    }

    public InterpretationBuilder<E> actionPrompt(String actionSystemPrompt) {
        this.actionSystemPrompt = actionSystemPrompt;
        return this;
    }

    public InterpretationBuilder<E> contextSupplier(Supplier<String> contextSupplier) {
        this.contextSupplier = contextSupplier;
        return this;
    }

    public ContentSummariser<E, LlmObservationResult> observationOnly() {
        return LlmSummarisers.observation(agentProvider, systemPrompt, policy,
            renderer, llmExecutor);
    }

    public ContentSummariser<E, InterpretationResult> combined() {
        return LlmSummarisers.combined(agentProvider, systemPrompt, policy,
            renderer, llmExecutor);
    }
}

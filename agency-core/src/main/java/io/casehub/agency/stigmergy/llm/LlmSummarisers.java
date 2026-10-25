package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.perception.observation.ObservationContext;
import io.casehub.agency.perception.observation.ObservationRenderer;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LlmSummarisers {

    private static final Logger LOG = Logger.getLogger(LlmSummarisers.class.getName());

    private LlmSummarisers() {}

    public static <E> ContentSummariser<E, LlmObservationResult> observation(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            ObservationRenderer<E> renderer,
            ExecutorService llmExecutor) {
        return (List<E> items, @Nullable LlmObservationResult previous) -> {
            var batchStart = Instant.now();
            var batchSize = items.size();

            return CompletableFuture.supplyAsync(() -> {
                try {
                    var rendered = renderer.render(List.of(), new ObservationContext(
                        System.currentTimeMillis(), 0))
                        .toCompletableFuture().join();

                    var userPrompt = buildObservationPrompt(items, rendered.renderedText());
                    var config = AgentSessionConfig.of(systemPrompt, userPrompt);

                    var invocationResult = StructuredAgentInvoker.invoke(
                        agentProvider, config, LlmObservationResponse.class);

                    var batchEnd = Instant.now();

                    if (invocationResult instanceof StructuredAgentInvoker.InvocationResult.Success<LlmObservationResponse> success) {
                        var response = success.value();
                        var observations = LlmResponseConverter.toObservations(response);
                        var avgConfidence = response.observations().stream()
                            .mapToDouble(LlmObservation::confidence).average().orElse(0.0);
                        return new LlmObservationResult(
                            observations, response.observations(), avgConfidence,
                            response.observations().isEmpty() ? "" :
                                response.observations().get(0).reasoning(),
                            batchStart, batchEnd, batchSize, success.metadata());
                    }

                    if (invocationResult instanceof StructuredAgentInvoker.InvocationResult.ParseError<?> pe) {
                        LOG.log(Level.WARNING, "LLM observation parse error: {0}", pe.parseError());
                    } else if (invocationResult instanceof StructuredAgentInvoker.InvocationResult.AgentError<?> ae) {
                        LOG.log(Level.WARNING, "LLM observation agent error: {0}", ae.reason());
                    }

                    return emptyObservationResult(batchStart, batchSize);

                } catch (Exception e) {
                    LOG.log(Level.WARNING, "LLM observation failed", e);
                    return emptyObservationResult(batchStart, batchSize);
                }
            }, llmExecutor);
        };
    }

    public static <E> ContentSummariser<E, InterpretationResult> combined(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            ObservationRenderer<E> renderer,
            ExecutorService llmExecutor) {
        return (List<E> items, @Nullable InterpretationResult previous) ->
            CompletableFuture.supplyAsync(() -> {
                var batchStart = Instant.now();
                var batchSize = items.size();
                try {
                    var rendered = renderer.render(List.of(), new ObservationContext(
                        System.currentTimeMillis(), 0))
                        .toCompletableFuture().join();

                    var userPrompt = buildObservationPrompt(items, rendered.renderedText());
                    var config = AgentSessionConfig.of(systemPrompt, userPrompt);

                    var obsResult = StructuredAgentInvoker.invoke(
                        agentProvider, config, LlmObservationResponse.class);

                    if (obsResult instanceof StructuredAgentInvoker.InvocationResult.Success<LlmObservationResponse> s) {
                        var observations = LlmResponseConverter.toObservations(s.value());
                        return new InterpretationResult(
                            observations, List.of(), 0.0, "",
                            batchStart, Instant.now(), batchSize, s.metadata());
                    }

                    return emptyInterpretationResult(batchStart, batchSize);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "LLM combined interpretation failed", e);
                    return emptyInterpretationResult(batchStart, batchSize);
                }
            }, llmExecutor);
    }

    public static ContentSummariser<LlmObservationResult, ActionResult> action(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            Supplier<String> contextSupplier,
            ExecutorService llmExecutor) {
        return (List<LlmObservationResult> items, @Nullable ActionResult previous) ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    var context = contextSupplier.get();
                    var obsText = new StringBuilder();
                    for (var batch : items) {
                        for (var obs : batch.rawObservations()) {
                            obsText.append("- ").append(obs.patternId())
                                .append(" (confidence: ").append(obs.confidence())
                                .append("): ").append(obs.reasoning()).append("\n");
                        }
                    }
                    var userPrompt = "Context:\n" + context +
                        "\n\nObservations:\n" + obsText;
                    var config = AgentSessionConfig.of(systemPrompt, userPrompt);

                    var result = StructuredAgentInvoker.invoke(
                        agentProvider, config, LlmActionResponse.class);

                    if (result instanceof StructuredAgentInvoker.InvocationResult.Success<LlmActionResponse> s) {
                        var actions = LlmResponseConverter.toRecommendedActions(s.value());
                        var avgConf = actions.stream()
                            .mapToDouble(RecommendedAction::confidence).average().orElse(0.0);
                        return new ActionResult(actions, avgConf, "", s.metadata());
                    }

                    return new ActionResult(List.of(), 0.0, "",
                        StructuredAgentInvoker.InvocationMetadata.EMPTY);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "LLM action decision failed", e);
                    return new ActionResult(List.of(), 0.0, "",
                        StructuredAgentInvoker.InvocationMetadata.EMPTY);
                }
            }, llmExecutor);
    }

    private static <E> String buildObservationPrompt(List<E> items, String renderedContext) {
        var sb = new StringBuilder();
        if (!renderedContext.isBlank()) {
            sb.append("Environment state:\n").append(renderedContext).append("\n\n");
        }
        sb.append("Events in this batch (").append(items.size()).append("):\n");
        for (var item : items) {
            sb.append("- ").append(item).append("\n");
        }
        return sb.toString();
    }

    private static LlmObservationResult emptyObservationResult(Instant batchStart,
                                                                 int batchSize) {
        return new LlmObservationResult(
            List.of(), List.of(), 0.0, "",
            batchStart, Instant.now(), batchSize,
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
    }

    private static InterpretationResult emptyInterpretationResult(Instant batchStart,
                                                                    int batchSize) {
        return new InterpretationResult(
            List.of(), List.of(), 0.0, "",
            batchStart, Instant.now(), batchSize,
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
    }
}

package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.perception.observation.ObservationContext;
import io.casehub.agency.perception.observation.ObservationRenderer;
import io.casehub.agency.perception.observation.ObservationResult;
import io.casehub.agency.summarisation.EmissionPolicy;
import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.SummarisationRunner;
import io.casehub.agency.summarisation.WindowPolicy;
import io.casehub.api.spi.observation.Observation;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmInterpretationIntegrationTest {

    static final EventLevel INPUT_LEVEL = new EventLevel("env", 0);
    static final EventLevel OUTPUT_LEVEL = new EventLevel("obs", 1);

    record EnvEvent(Set<String> changedKeys, String data) {}

    @Test
    void fullPipeline_eventsAccumulate_tickTriggersLlm_handlersReceiveObservations()
            throws Exception {

        // --- Mock AgentProvider ---
        var agentProvider = mock(AgentProvider.class);
        var llmJson = """
            {"observations":[
                {"patternId":"risk-spike","confidence":0.92,"evidence":["risk.score"],"derivedFrom":[],"details":{"delta":0.37},"reasoning":"Risk jumped significantly"},
                {"patternId":"compound-alert","confidence":0.85,"evidence":["tx.amount"],"derivedFrom":["risk-spike"],"details":{},"reasoning":"Compound pattern detected"}
            ]}
            """;
        when(agentProvider.invoke(any())).thenReturn(
            Multi.createFrom().item(new AgentEvent.TextDelta(llmJson)));

        // --- Mock EnvironmentWriter ---
        var writer = mock(EnvironmentWriter.class);
        var caseId = UUID.randomUUID();

        // --- Observation renderer ---
        ObservationRenderer<EnvEvent> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(
                new ObservationResult("Env state snapshot", List.of(),
                    events.size(), 0, null));

        // --- Build LLM summariser with InFlightGate ---
        var llmExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var baseSummariser = LlmSummarisers.<EnvEvent>observation(
            agentProvider,
            "You are an environment observer. Identify patterns.",
            LlmInvocationPolicy.of(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)),
            renderer,
            llmExecutor);

        var gate = new InFlightGate<>(baseSummariser);

        // --- Output bus + subscriber ---
        var outputBus = new EventStreamBus<LlmObservationResult>();
        var receivedResults = new ArrayList<LlmObservationResult>();
        var latch = new CountDownLatch(1);

        // --- Observation dispatcher ---
        var dispatchedPatterns = new ArrayList<String>();
        var dispatchOrder = new ArrayList<String>();
        var dispatcher = ObservationDispatcher.builder()
            .on("risk-spike", (obs, raw, ctx) -> {
                dispatchOrder.add("risk-spike");
                dispatchedPatterns.add(obs.patternId());
                ctx.storeObservation(obs);
            })
            .on("compound-alert", (obs, raw, ctx) -> {
                dispatchOrder.add("compound-alert");
                dispatchedPatterns.add(obs.patternId());
                ctx.depositSignal("alert:" + obs.patternId(), obs.confidence());
            })
            .build();

        outputBus.subscribe(
            e -> true,
            event -> {
                var result = event.payload();
                receivedResults.add(result);
                var ctx = new ObservationHandlerContext(
                    caseId, "agent1", "tenant1", writer, result);
                dispatcher.dispatch(result, ctx);
                latch.countDown();
            });

        // --- Build emission policy: change-gated + in-flight gated ---
        Function<LevelEvent<EnvEvent>, Set<String>> keyExtractor =
            e -> e.payload().changedKeys();
        var changeGated = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            keyExtractor,
            EmissionPolicy.window(WindowPolicy.ofCount(2)));
        var gatedPolicy = gate.<Object>gating(changeGated);

        // --- Build SummarisationRunner ---
        var runner = SummarisationRunner.<EnvEvent, LlmObservationResult>builder(
                gate.summariser().asSummariser(), outputBus, OUTPUT_LEVEL)
            .emissionPolicy(gatedPolicy)
            .build();

        // --- Feed events ---
        runner.collect(new LevelEvent<>(
            new EnvEvent(Set.of("risk.score"), "score=0.8"),
            1000L, INPUT_LEVEL, "tenant1"));
        runner.collect(new LevelEvent<>(
            new EnvEvent(Set.of("risk.score"), "score=0.95"),
            2000L, INPUT_LEVEL, "tenant1"));

        // --- Tick triggers LLM invocation ---
        runner.tick(3000L).toCompletableFuture().get(10, TimeUnit.SECONDS);

        // --- Wait for handler dispatch ---
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        // --- Verify results ---
        assertThat(receivedResults).hasSize(1);
        var result = receivedResults.get(0);
        assertThat(result.observations()).hasSize(2);
        assertThat(result.rawObservations()).hasSize(2);
        assertThat(result.batchEventCount()).isEqualTo(2);

        // Verify topological ordering: leaf before derived
        assertThat(dispatchOrder).containsExactly("risk-spike", "compound-alert");
        assertThat(dispatchedPatterns).containsExactly("risk-spike", "compound-alert");
    }

    @Test
    void changeGating_suppressesWhenNoWatchedKeysChanged() throws Exception {
        var agentProvider = mock(AgentProvider.class);
        var outputBus = new EventStreamBus<LlmObservationResult>();
        var received = new ArrayList<LlmObservationResult>();
        outputBus.subscribe(e -> true, event -> received.add(event.payload()));

        ObservationRenderer<EnvEvent> renderer = (events, obsCtx) ->
            CompletableFuture.completedFuture(
                new ObservationResult("rendered", List.of(), events.size(), 0, null));

        var summariser = LlmSummarisers.<EnvEvent>observation(
            agentProvider,
            "prompt",
            LlmInvocationPolicy.of(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30)),
            renderer,
            Executors.newVirtualThreadPerTaskExecutor());

        Function<LevelEvent<EnvEvent>, Set<String>> keyExtractor =
            e -> e.payload().changedKeys();
        var changeGated = new ChangeGatedEmissionPolicy<>(
            Set.of("risk.*"),
            keyExtractor,
            EmissionPolicy.window(WindowPolicy.ofCount(1)));

        var runner = SummarisationRunner.<EnvEvent, LlmObservationResult>builder(
                summariser.asSummariser(), outputBus, OUTPUT_LEVEL)
            .emissionPolicy(changeGated)
            .build();

        // Feed event with non-watched key
        runner.collect(new LevelEvent<>(
            new EnvEvent(Set.of("account.name"), "changed"),
            1000L, INPUT_LEVEL, null));

        runner.tick(2000L).toCompletableFuture().get(5, TimeUnit.SECONDS);

        // AgentProvider should NOT have been called
        assertThat(received).isEmpty();
    }

    @Test
    void inFlightGate_suppressesSecondTickWhileFirstInFlight() throws Exception {
        var pending = new CompletableFuture<LlmObservationResult>();
        var callCount = new int[]{0};

        var baseSummariser = new io.casehub.agency.summarisation.ContentSummariser<EnvEvent, LlmObservationResult>() {
            @Override
            public java.util.concurrent.CompletionStage<LlmObservationResult> summarise(
                    List<EnvEvent> items, LlmObservationResult previous) {
                callCount[0]++;
                return pending;
            }
        };

        var gate = new InFlightGate<>(baseSummariser);
        var outputBus = new EventStreamBus<LlmObservationResult>();

        var gated = gate.<Object>gating(EmissionPolicy.window(WindowPolicy.ofCount(1)));

        var runner = SummarisationRunner.<EnvEvent, LlmObservationResult>builder(
                gate.summariser().asSummariser(), outputBus, OUTPUT_LEVEL)
            .emissionPolicy(gated)
            .build();

        // First event + tick → starts LLM call
        runner.collect(new LevelEvent<>(
            new EnvEvent(Set.of("risk.score"), "data"),
            1000L, INPUT_LEVEL, null));
        runner.tick(2000L);

        // Second event + tick while first is in-flight → suppressed
        runner.collect(new LevelEvent<>(
            new EnvEvent(Set.of("risk.score"), "data2"),
            3000L, INPUT_LEVEL, null));
        runner.tick(4000L);

        assertThat(callCount[0]).isEqualTo(1);
    }
}

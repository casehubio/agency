package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionNarrativePipelineTest {

    private static final Instant T1 = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-11T10:00:01Z");

    private EventStreamBus<DecisionSignal> signalBus;
    private EventStreamBus<StepDecisionSummary> stepBus;
    private EventStreamBus<DecisionNarrative> narrativeBus;
    private DecisionNarrativePipeline pipeline;

    private List<StepDecisionSummary> capturedStepSummaries;
    private List<DecisionNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        signalBus = new EventStreamBus<>();
        stepBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedStepSummaries = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        stepBus.subscribe(e -> true, e -> capturedStepSummaries.add(e.payload()));
        narrativeBus.subscribe(e -> true, e -> capturedNarratives.add(e.payload()));

        var l1 = new KeyedSummarisationRunner<>(
                (LevelEvent<DecisionSignal> e) -> e.payload().caseId() + ":" + e.payload().stepName(),
                group -> group.stream().anyMatch(e -> e.payload() instanceof StepOutcome),
                30_000L,
                Summariser.ofSync(new DecisionSignalSummariser()),
                stepBus, new EventLevel("step-summaries", 1));

        var templateSummariser = new TemplateFallbackSummariser();
        var l2 = new KeyedSummarisationRunner<>(
                (LevelEvent<StepDecisionSummary> e) -> e.payload().caseId(),
                group -> group.size() >= 1,
                60_000L,
                templateSummariser.asSummariser(),
                narrativeBus, new EventLevel("decision-narratives", 2));

        pipeline = new DecisionNarrativePipeline(signalBus, stepBus, narrativeBus, l1, l2);
    }

    @Test
    void endToEnd_signalsThroughPipeline_producesNarrative() {
        var routing = new RoutingDecision("case1", "route-analyst", T1, "analyst-3", "cbr", 0.87, List.of("a1", "a2", "a3"), null);
        var outcome = new StepOutcome("case1", "route-analyst", T2, "COMPLETED", "analyst-3", null, Duration.ofSeconds(1));

        signalBus.publish(new LevelEvent<>(routing, T1.toEpochMilli(), DecisionNarrativePipeline.L0_SIGNALS, null));
        signalBus.publish(new LevelEvent<>(outcome, T2.toEpochMilli(), DecisionNarrativePipeline.L0_SIGNALS, null));

        pipeline.tick(T2.toEpochMilli());
        pipeline.tick(T2.toEpochMilli() + 1);

        assertThat(capturedStepSummaries).hasSize(1);
        assertThat(capturedStepSummaries.get(0).signals()).hasSize(2);

        assertThat(capturedNarratives).hasSize(1);
        assertThat(capturedNarratives.get(0).caseId()).isEqualTo("case1");
    }

    @Test
    void multipleSteps_incrementalNarrative() {
        emitStepSignals("case1", "step1", T1);
        pipeline.tick(T1.toEpochMilli());
        pipeline.tick(T1.toEpochMilli() + 1);

        emitStepSignals("case1", "step2", T2);
        pipeline.tick(T2.toEpochMilli());
        pipeline.tick(T2.toEpochMilli() + 1);

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).stepNames()).contains("step1", "step2");
    }

    @Test
    void evictCaseState_resetsState() {
        emitStepSignals("case1", "step1", T1);
        pipeline.tick(T1.toEpochMilli());
        pipeline.tick(T1.toEpochMilli() + 1);

        pipeline.evictCaseState("case1");

        emitStepSignals("case1", "step2", T2);
        pipeline.tick(T2.toEpochMilli());
        pipeline.tick(T2.toEpochMilli() + 1);

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).stepNames()).containsExactly("step2");
    }

    @Test
    void differentCases_isolatedState() {
        emitStepSignals("case1", "step1", T1);
        emitStepSignals("case2", "step1", T1);
        pipeline.tick(T1.toEpochMilli());
        pipeline.tick(T1.toEpochMilli() + 1);

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.stream().map(DecisionNarrative::caseId).distinct().toList())
                .containsExactlyInAnyOrder("case1", "case2");
    }

    private void emitStepSignals(String caseId, String stepName, Instant t) {
        var outcome = new StepOutcome(caseId, stepName, t, "COMPLETED", "w1", null, Duration.ofSeconds(1));
        signalBus.publish(new LevelEvent<>(outcome, t.toEpochMilli(), DecisionNarrativePipeline.L0_SIGNALS, null));
    }

    static class TemplateFallbackSummariser
            implements io.casehub.blocks.summarisation.ContentSummariser<StepDecisionSummary, DecisionNarrative> {
        @Override
        public CompletableFuture<DecisionNarrative> summarise(
                List<StepDecisionSummary> items, DecisionNarrative previous) {
            var stepNames = new ArrayList<String>();
            if (previous != null) stepNames.addAll(previous.stepNames());
            items.forEach(s -> stepNames.add(s.stepName()));

            var sb = new StringBuilder();
            if (previous != null) sb.append(previous.explanation()).append(" ");
            items.forEach(s -> s.signals().forEach(d -> sb.append(d.summary()).append(". ")));

            var sources = items.stream()
                    .flatMap(s -> s.signals().stream())
                    .map(SignalDigest::signalType).distinct().toList();
            var confidence = items.stream()
                    .flatMap(s -> s.signals().stream())
                    .mapToDouble(SignalDigest::confidence).min().orElse(0.5);

            return CompletableFuture.completedFuture(
                    new DecisionNarrative(items.get(0).caseId(), stepNames, sb.toString().strip(),
                            sources, confidence, Instant.now()));
        }
    }
}

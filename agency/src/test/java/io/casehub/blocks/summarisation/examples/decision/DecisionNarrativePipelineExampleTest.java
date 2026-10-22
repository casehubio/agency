package io.casehub.blocks.summarisation.examples.decision;

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

import static org.assertj.core.api.Assertions.assertThat;

class DecisionNarrativePipelineExampleTest {

    static final EventLevel L0 = new EventLevel("trading-signals", 0);
    static final EventLevel L1 = new EventLevel("step-summaries", 1);
    static final EventLevel L2 = new EventLevel("narratives", 2);

    EventStreamBus<TradingSignal> signalBus;
    EventStreamBus<TradeStepSummary> stepBus;
    EventStreamBus<TradeNarrative> narrativeBus;
    KeyedSummarisationRunner<String, TradingSignal, TradeStepSummary> l1;
    KeyedSummarisationRunner<String, TradeStepSummary, TradeNarrative> l2;

    List<TradeStepSummary> capturedSteps;
    List<TradeNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        signalBus = new EventStreamBus<>();
        stepBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedSteps = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        stepBus.subscribe(e -> true, e -> capturedSteps.add(e.payload()));
        narrativeBus.subscribe(e -> true, e -> capturedNarratives.add(e.payload()));

        l1 = new KeyedSummarisationRunner<>(
                e -> e.payload().tradeId() + ":" + e.payload().step(),
                group -> group.stream().anyMatch(e -> e.payload() instanceof TradeResult),
                5_000L,
                Summariser.ofSync(new TradingSignalSummariser()),
                stepBus, L1);

        l2 = new KeyedSummarisationRunner<>(
                e -> e.payload().tradeId(),
                group -> group.size() >= 1,
                10_000L,
                new TradeNarrativeSummariser().asSummariser(),
                narrativeBus, L2);

        signalBus.subscribe(e -> true, l1::collect);
        stepBus.subscribe(e -> true, l2::collect);
    }

    @Test
    void singleTrade_threeSteps_producesIncrementalNarrative() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit(new AnalystRouting("trade-1", "select-analyst", t, "analyst-3", 0.87));
        emit(new HistoricalMatch("trade-1", "select-analyst", t.plusMillis(10), 5, 0.92));
        emit(new TradeResult("trade-1", "select-analyst", t.plusMillis(20), "ROUTED", Duration.ofMillis(20)));
        tick(t);

        assertThat(capturedSteps).hasSize(1);
        assertThat(capturedSteps.get(0).signalFacts()).hasSize(3);

        tick(t.plusMillis(1));
        assertThat(capturedNarratives).hasSize(1);

        emit(new TradeResult("trade-1", "execute", t.plusSeconds(2), "FILLED", Duration.ofSeconds(1)));
        tick(t.plusSeconds(2));
        tick(t.plusSeconds(2).plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).steps()).containsExactly("select-analyst", "execute");
    }

    @Test
    void multipleTrades_isolatedPipelines() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit(new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)));
        emit(new TradeResult("trade-2", "step1", t, "OK", Duration.ofMillis(10)));
        tick(t);
        tick(t.plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        var tradeIds = capturedNarratives.stream().map(TradeNarrative::tradeId).toList();
        assertThat(tradeIds).containsExactlyInAnyOrder("trade-1", "trade-2");
    }

    @Test
    void multiTenant_signalsDontCrossContaminate() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        signalBus.publish(new LevelEvent<>(
                new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)),
                t.toEpochMilli(), L0, "tenant-A"));
        signalBus.publish(new LevelEvent<>(
                new TradeResult("trade-2", "step1", t, "OK", Duration.ofMillis(10)),
                t.toEpochMilli(), L0, "tenant-B"));

        tick(t);
        tick(t.plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
    }

    @Test
    void staleTimeout_firesForIncompleteGroups() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit(new AnalystRouting("trade-1", "step1", t, "analyst-1", 0.5));
        l1.tick(t.toEpochMilli() + 6_000);

        assertThat(capturedSteps).hasSize(1);
    }

    @Test
    void eviction_resetsL2State() {
        var t = Instant.parse("2026-09-11T10:00:00Z");

        emit(new TradeResult("trade-1", "step1", t, "OK", Duration.ofMillis(10)));
        tick(t);
        tick(t.plusMillis(1));

        l2.evictState("trade-1");

        emit(new TradeResult("trade-1", "step2", t.plusSeconds(1), "OK", Duration.ofMillis(10)));
        tick(t.plusSeconds(1));
        tick(t.plusSeconds(1).plusMillis(1));

        assertThat(capturedNarratives).hasSize(2);
        assertThat(capturedNarratives.get(1).steps()).containsExactly("step2");
    }

    private void emit(TradingSignal signal) {
        signalBus.publish(new LevelEvent<>(signal, signal.timestamp().toEpochMilli(), L0, null));
    }

    private void tick(Instant t) {
        l1.tick(t.toEpochMilli());
        l2.tick(t.toEpochMilli());
    }
}

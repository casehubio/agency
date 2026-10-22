package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AbstractNarrativeSignalStrategyTest {

    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void emit_publishesToSignalBus() {
        var signalBus = new EventStreamBus<DecisionSignal>();
        var received = new ArrayList<DecisionSignal>();
        signalBus.subscribe(e -> true, e -> received.add(e.payload()));

        var pipeline = mock(DecisionNarrativePipeline.class);
        var strategy = new TestSignalStrategy(signalBus, pipeline);

        var signal = new StepOutcome("case1", "step1", NOW, "COMPLETED", "w1", null, Duration.ofSeconds(1));
        strategy.emit(signal);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo(signal);
    }

    @Test
    void emit_setsCorrectEventLevel() {
        var signalBus = new EventStreamBus<DecisionSignal>();
        var received = new ArrayList<LevelEvent<DecisionSignal>>();
        signalBus.subscribe(e -> true, received::add);

        var pipeline = mock(DecisionNarrativePipeline.class);
        var strategy = new TestSignalStrategy(signalBus, pipeline);

        strategy.emit(new StepOutcome("c1", "s1", NOW, "COMPLETED", null, null, Duration.ofSeconds(1)));

        assertThat(received.get(0).level().name()).isEqualTo("decision-signal");
    }

    static class TestSignalStrategy extends AbstractNarrativeSignalStrategy {
        TestSignalStrategy(EventStreamBus<DecisionSignal> signalBus, DecisionNarrativePipeline pipeline) {
            super(signalBus, pipeline);
        }

        @Override
        protected @Nullable String extractTenancyId(DecisionSignal signal) {
            return null;
        }

        @Override
        public void onStepOutcome(Object event) {}
    }
}

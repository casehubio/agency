package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.SummarisationRunner;
import io.casehub.agency.summarisation.Tickable;
import io.casehub.agency.summarisation.WindowPolicy;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Auto-triggered two-phase pipeline: observation → action.
 * <p>
 * Subscribes to the observation runner's output bus and feeds each observation
 * result into the action runner's input. Eliminates the ~15 lines of manual
 * {@link EventStreamBus} subscription wiring that consumers would otherwise write.
 * <p>
 * Usage:
 * <pre>{@code
 * var pipeline = ObservationActionPipeline.builder()
 *     .observationSummariser(obsContentSummariser)
 *     .actionSummariser(actionContentSummariser)
 *     .observationWindow(WindowPolicy.ofCount(10))
 *     .actionWindow(WindowPolicy.ofCount(1))
 *     .build();
 *
 * // Feed raw events
 * pipeline.collectObservation(event);
 *
 * // Tick both phases
 * pipeline.tick(now);
 * }</pre>
 */
public class ObservationActionPipeline<E> implements Tickable {

    private static final EventLevel OBSERVATION_LEVEL = new EventLevel("observation", 1);
    private static final EventLevel ACTION_LEVEL = new EventLevel("action", 2);

    private final SummarisationRunner<E, LlmObservationResult> observationRunner;
    private final SummarisationRunner<LlmObservationResult, ActionResult> actionRunner;
    private final EventStreamBus<ActionResult> actionOutputBus;

    private ObservationActionPipeline(
            SummarisationRunner<E, LlmObservationResult> observationRunner,
            SummarisationRunner<LlmObservationResult, ActionResult> actionRunner,
            EventStreamBus<ActionResult> actionOutputBus) {
        this.observationRunner = observationRunner;
        this.actionRunner = actionRunner;
        this.actionOutputBus = actionOutputBus;
    }

    public void collectObservation(LevelEvent<E> event) {
        observationRunner.collect(event);
    }

    @Override
    public CompletionStage<Void> tick(long now) {
        return observationRunner.tick(now)
                .thenCompose(v -> actionRunner.tick(now));
    }

    @Override
    public CompletionStage<Void> flush() {
        return observationRunner.flush()
                .thenCompose(v -> actionRunner.flush());
    }

    public EventStreamBus<ActionResult> actionOutputBus() {
        return actionOutputBus;
    }

    public SummarisationRunner<E, LlmObservationResult> observationRunner() {
        return observationRunner;
    }

    public SummarisationRunner<LlmObservationResult, ActionResult> actionRunner() {
        return actionRunner;
    }

    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    public static final class Builder<E> {
        private ContentSummariser<E, LlmObservationResult> observationSummariser;
        private ContentSummariser<LlmObservationResult, ActionResult> actionSummariser;
        private WindowPolicy observationWindow = WindowPolicy.ofCount(10);
        private WindowPolicy actionWindow = WindowPolicy.ofCount(1);

        private Builder() {}

        public Builder<E> observationSummariser(ContentSummariser<E, LlmObservationResult> summariser) {
            this.observationSummariser = summariser;
            return this;
        }

        public Builder<E> actionSummariser(ContentSummariser<LlmObservationResult, ActionResult> summariser) {
            this.actionSummariser = summariser;
            return this;
        }

        public Builder<E> observationWindow(WindowPolicy policy) {
            this.observationWindow = policy;
            return this;
        }

        public Builder<E> actionWindow(WindowPolicy policy) {
            this.actionWindow = policy;
            return this;
        }

        public ObservationActionPipeline<E> build() {
            Objects.requireNonNull(observationSummariser, "observationSummariser is required");
            Objects.requireNonNull(actionSummariser, "actionSummariser is required");

            var observationOutputBus = new EventStreamBus<LlmObservationResult>();
            var actionOutputBus = new EventStreamBus<ActionResult>();

            var obsRunner = new SummarisationRunner<>(
                    observationWindow, observationSummariser.asSummariser(),
                    observationOutputBus, OBSERVATION_LEVEL);

            var actionRunner = new SummarisationRunner<>(
                    actionWindow, actionSummariser.asSummariser(),
                    actionOutputBus, ACTION_LEVEL);

            observationOutputBus.subscribe(
                    obs -> true,
                    event -> actionRunner.collect(event));

            return new ObservationActionPipeline<>(obsRunner, actionRunner, actionOutputBus);
        }
    }
}

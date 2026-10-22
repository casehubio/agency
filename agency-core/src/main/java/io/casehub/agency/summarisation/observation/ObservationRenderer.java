package io.casehub.agency.summarisation.observation;

import io.casehub.agency.summarisation.LevelEvent;

import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ObservationRenderer<E> {
    CompletionStage<ObservationResult> render(
            List<LevelEvent<E>> events, ObservationContext context);
}

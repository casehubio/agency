package io.casehub.agency.summarisation;

import java.util.List;

@FunctionalInterface
public interface Compactor<E> {
    List<LevelEvent<E>> compact(List<LevelEvent<E>> events);
}

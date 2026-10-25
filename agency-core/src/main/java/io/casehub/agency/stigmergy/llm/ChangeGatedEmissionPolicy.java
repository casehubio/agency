package io.casehub.agency.stigmergy.llm;

import io.casehub.agency.summarisation.EmissionPolicy;
import io.casehub.agency.summarisation.LevelEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class ChangeGatedEmissionPolicy<E> implements EmissionPolicy<E, Object> {

    private final Set<String> watchedKeys;
    private final Function<LevelEvent<E>, Set<String>> changedKeyExtractor;
    private final EmissionPolicy<E, ?> delegate;

    public ChangeGatedEmissionPolicy(Set<String> watchedKeys,
                                     Function<LevelEvent<E>, Set<String>> changedKeyExtractor,
                                     EmissionPolicy<E, ?> delegate) {
        this.watchedKeys = Set.copyOf(watchedKeys);
        this.changedKeyExtractor = changedKeyExtractor;
        this.delegate = delegate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean shouldEmit(List<LevelEvent<E>> buffered,
                              @Nullable Object currentState, long now) {
        if (buffered.isEmpty()) return false;
        if (!anyWatchedKeyChanged(buffered)) return false;
        return ((EmissionPolicy<E, Object>) delegate).shouldEmit(buffered, currentState, now);
    }

    private boolean anyWatchedKeyChanged(List<LevelEvent<E>> buffered) {
        for (var event : buffered) {
            var changed = changedKeyExtractor.apply(event);
            for (var changedKey : changed) {
                if (matchesAnyWatched(changedKey)) return true;
            }
        }
        return false;
    }

    private boolean matchesAnyWatched(String changedKey) {
        for (var watched : watchedKeys) {
            if (watched.endsWith(".*")) {
                var prefix = watched.substring(0, watched.length() - 1);
                if (changedKey.startsWith(prefix)) return true;
            } else {
                if (watched.equals(changedKey)) return true;
            }
        }
        return false;
    }
}

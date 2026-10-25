package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.Observation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ObservationDispatcher {

    private static final Logger LOG = Logger.getLogger(ObservationDispatcher.class.getName());

    private final Map<String, ObservationHandler> handlers;
    private final @Nullable ObservationHandler derivedHandler;
    private final @Nullable ObservationHandler unhandledHandler;

    private ObservationDispatcher(Map<String, ObservationHandler> handlers,
                                  @Nullable ObservationHandler derivedHandler,
                                  @Nullable ObservationHandler unhandledHandler) {
        this.handlers = Map.copyOf(handlers);
        this.derivedHandler = derivedHandler;
        this.unhandledHandler = unhandledHandler;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void dispatch(LlmObservationResult result, ObservationHandlerContext context) {
        var sorted = topologicalSort(result.rawObservations());
        var engineByPatternId = indexByPatternId(result.observations());

        for (var raw : sorted) {
            var engine = engineByPatternId.get(raw.patternId());
            if (engine == null) continue;

            var handler = resolveHandler(raw);
            if (handler != null) {
                try {
                    handler.handle(engine, raw, context);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Handler for ''{0}'' threw exception",
                        new Object[]{raw.patternId()});
                    LOG.log(Level.WARNING, e.getMessage(), e);
                }
            }
        }

        context.flushObservations();
    }

    private @Nullable ObservationHandler resolveHandler(LlmObservation raw) {
        var specific = handlers.get(raw.patternId());
        if (specific != null) return specific;

        if (!raw.derivedFrom().isEmpty() && derivedHandler != null) {
            return derivedHandler;
        }

        return unhandledHandler;
    }

    private static Map<String, Observation> indexByPatternId(List<Observation> observations) {
        var index = new HashMap<String, Observation>();
        for (var obs : observations) {
            index.put(obs.patternId(), obs);
        }
        return index;
    }

    static List<LlmObservation> topologicalSort(List<LlmObservation> observations) {
        var byId = new LinkedHashMap<String, LlmObservation>();
        for (var obs : observations) {
            byId.put(obs.patternId(), obs);
        }

        var inDegree = new HashMap<String, Integer>();
        var adjacency = new HashMap<String, List<String>>();
        for (var obs : observations) {
            inDegree.putIfAbsent(obs.patternId(), 0);
            adjacency.putIfAbsent(obs.patternId(), new ArrayList<>());
            for (var parent : obs.derivedFrom()) {
                if (byId.containsKey(parent)) {
                    adjacency.computeIfAbsent(parent, k -> new ArrayList<>()).add(obs.patternId());
                    inDegree.merge(obs.patternId(), 1, Integer::sum);
                }
            }
        }

        var queue = new ArrayDeque<String>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        var sorted = new ArrayList<LlmObservation>();
        var visited = new java.util.HashSet<String>();
        while (!queue.isEmpty()) {
            var id = queue.poll();
            if (!visited.add(id)) continue;
            var obs = byId.get(id);
            if (obs != null) sorted.add(obs);
            for (var child : adjacency.getOrDefault(id, List.of())) {
                var deg = inDegree.merge(child, -1, Integer::sum);
                if (deg == 0) queue.add(child);
            }
        }

        // cycle fallback: add any nodes not yet visited
        for (var obs : observations) {
            if (!visited.contains(obs.patternId())) {
                LOG.log(Level.SEVERE,
                    "Cycle detected involving observation ''{0}'' — dispatching in batch order",
                    obs.patternId());
                sorted.add(obs);
            }
        }

        return sorted;
    }

    public static final class Builder {
        private final Map<String, ObservationHandler> handlers = new LinkedHashMap<>();
        private @Nullable ObservationHandler derivedHandler;
        private @Nullable ObservationHandler unhandledHandler;

        private Builder() {}

        public Builder on(String patternId, ObservationHandler handler) {
            handlers.put(patternId, handler);
            return this;
        }

        public Builder onDerived(ObservationHandler handler) {
            this.derivedHandler = handler;
            return this;
        }

        public Builder onUnhandled(ObservationHandler handler) {
            this.unhandledHandler = handler;
            return this;
        }

        public ObservationDispatcher build() {
            return new ObservationDispatcher(handlers, derivedHandler, unhandledHandler);
        }
    }
}

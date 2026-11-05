package io.casehub.agency.orchestration.topology;

import io.casehub.api.model.coordination.DecisionScope;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GraduationState {

    private final Map<String, DecisionScope> agentScopes;
    private final Map<String, List<FeedbackSignal>> recentSignals;
    private final int windowSize;
    private volatile DecisionScope scopeFloor;


    public GraduationState(Map<String, DecisionScope> initialScopes, int windowSize) {
        this.agentScopes = new ConcurrentHashMap<>(initialScopes);
        this.recentSignals = new ConcurrentHashMap<>();
        this.windowSize = windowSize;
    }

    public DecisionScope scopeFor(String agentId) {
        return agentScopes.getOrDefault(agentId, DecisionScope.STIMULUS_DRIVEN);
    }

    public void forceScope(String agentId, DecisionScope scope) {
        this.scopeFloor = scope;
        agentScopes.put(agentId, scope);
    }


    public void recordFeedback(String agentId, FeedbackSignal signal) {
        recentSignals.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(signal);
        adjustScope(agentId);
    }

    private void adjustScope(String agentId) {
        var signals = recentSignals.getOrDefault(agentId, List.of());
        int from = Math.max(0, signals.size() - windowSize);
        var window = signals.subList(from, signals.size());

        if (window.stream().anyMatch(s -> s == FeedbackSignal.SCOPE_VIOLATION)) {
            downgrade(agentId);
            return;
        }

        long successes = window.stream().filter(s -> s == FeedbackSignal.SUCCESS).count();
        long failures = window.stream().filter(s -> s == FeedbackSignal.FAILURE).count();

        if (successes >= windowSize * 0.8) {
            upgrade(agentId);
        } else if (failures >= windowSize * 0.5) {
            downgrade(agentId);
        }
    }

    public void upgrade(String agentId) {
        agentScopes.compute(agentId, (id, scope) -> {
            var upgraded = switch (scope != null ? scope : DecisionScope.STIMULUS_DRIVEN) {
                case STIMULUS_DRIVEN -> DecisionScope.TASK_ADAPTATION;
                case TASK_ADAPTATION -> DecisionScope.FULL_AUTONOMY;
                case FULL_AUTONOMY -> DecisionScope.FULL_AUTONOMY;
            };
            if (scopeFloor != null && upgraded.ordinal() > scopeFloor.ordinal()) {
                return scopeFloor;
            }
            return upgraded;
        });
    }

    public void downgrade(String agentId) {
        agentScopes.compute(agentId, (id, scope) ->
            switch (scope != null ? scope : DecisionScope.STIMULUS_DRIVEN) {
                case FULL_AUTONOMY -> DecisionScope.TASK_ADAPTATION;
                case TASK_ADAPTATION -> DecisionScope.STIMULUS_DRIVEN;
                case STIMULUS_DRIVEN -> DecisionScope.STIMULUS_DRIVEN;
            });
    }
}

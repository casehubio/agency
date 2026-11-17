package io.casehub.agency.orchestration.degradation;

import io.casehub.platform.agent.budget.BudgetPriority;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class CascadeHandler {

    private final Set<String> degradedLoops = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cascadeTriggered = new AtomicBoolean(false);
    private final @Nullable CascadeThreshold threshold;
    private final Map<String, BudgetPriority> loopPriorities;
    private final List<CascadeDegradationTrigger> cascadeTriggers;
    private final Runnable cascadeAction;

    public CascadeHandler(@Nullable CascadeThreshold threshold,
                          Map<String, BudgetPriority> loopPriorities,
                          List<CascadeDegradationTrigger> cascadeTriggers,
                          Runnable cascadeAction) {
        this.threshold       = threshold;
        this.loopPriorities  = Map.copyOf(loopPriorities);
        this.cascadeTriggers = List.copyOf(cascadeTriggers);
        this.cascadeAction   = cascadeAction;
    }

    public CascadeHandler(@Nullable CascadeThreshold threshold,
                          Map<String, BudgetPriority> loopPriorities,
                          List<CascadeDegradationTrigger> cascadeTriggers) {
        this(threshold, loopPriorities, cascadeTriggers, () -> {});
    }

    public void onDegradation(DegradedEvent event) {
        if (event.fallbackActive()) {
            degradedLoops.add(event.capability());
        } else {
            degradedLoops.remove(event.capability());
        }
        if (event.fallbackActive() && !cascadeTriggered.get()
            && threshold != null
            && threshold.isBreached(degradedLoops, loopPriorities.size(), loopPriorities)
            && cascadeTriggered.compareAndSet(false, true)) {
            cascadeAction.run();
            cascadeTriggers.forEach(CascadeDegradationTrigger::activate);
        }
    }

    public Set<String> degradedLoops() { return Set.copyOf(degradedLoops); }

    public boolean isCascadeTriggered() { return cascadeTriggered.get(); }
}

package io.casehub.agency.summarisation;

import org.jspecify.annotations.Nullable;

import java.util.List;

class WindowPolicyEmission<IN, S> implements EmissionPolicy<IN, S> {

    private final WindowPolicy policy;

    WindowPolicyEmission(WindowPolicy policy) {
        this.policy = policy;
    }

    @Override
    public boolean shouldEmit(List<LevelEvent<IN>> buffered,
                              @Nullable S currentState, long now) {
        if (buffered.isEmpty()) return false;
        if (policy.maxCount() > 0 && buffered.size() >= policy.maxCount())
            return true;
        if (policy.maxAge() > 0) {
            long oldest = buffered.get(0).timestamp();
            return (now - oldest) >= policy.maxAge();
        }
        return false;
    }
}

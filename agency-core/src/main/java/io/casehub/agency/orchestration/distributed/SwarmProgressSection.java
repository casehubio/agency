package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.DecisionContext;
import io.casehub.agency.orchestration.loop.LoopPromptSection;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class SwarmProgressSection implements LoopPromptSection {

    private final Supplier<@Nullable SwarmProgress> progressSupplier;

    public SwarmProgressSection(Supplier<@Nullable SwarmProgress> progressSupplier) {
        this.progressSupplier = progressSupplier;
    }

    @Override
    public String contribute(DecisionContext context) {
        var progress = progressSupplier.get();
        if (progress == null) return "";
        var sb = new StringBuilder("## Swarm Progress\n\n");
        sb.append("- Exploration: ").append(String.format("%.1f%%", progress.explorationPace() * 100)).append('\n');
        sb.append("- Consensus: ").append(String.format("%.1f%%", progress.consensusScore() * 100)).append('\n');
        sb.append("- Stability: ").append(String.format("%.1f%%", progress.stabilityScore() * 100)).append('\n');
        return sb.toString();
    }
}

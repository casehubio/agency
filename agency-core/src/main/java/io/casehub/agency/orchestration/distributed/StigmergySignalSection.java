package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.DecisionContext;
import io.casehub.agency.orchestration.loop.LoopPromptSection;

import java.util.List;
import java.util.function.Supplier;

public class StigmergySignalSection implements LoopPromptSection {

    private final Supplier<List<VisibleSignal>> signalSupplier;

    public StigmergySignalSection(Supplier<List<VisibleSignal>> signalSupplier) {
        this.signalSupplier = signalSupplier;
    }

    @Override
    public String contribute(DecisionContext context) {
        var signals = signalSupplier.get();
        if (signals == null || signals.isEmpty()) return "";
        var sb = new StringBuilder("## Stigmergy Signals\n\n");
        sb.append("| Signal | Strength | Reinforcements |\n");
        sb.append("|--------|----------|----------------|\n");
        for (var s : signals) {
            sb.append("| ").append(s.name())
              .append(" | ").append(String.format("%.2f", s.strength()))
              .append(" | ").append(s.reinforcementCount())
              .append(" |\n");
        }
        return sb.toString();
    }
}

package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class StigmergyWriteBackHandler implements Consumer<DecisionOutcome> {

    @FunctionalInterface
    public interface StigmergyWriter {
        void depositSignal(String agentId, String signalName, double strength);
    }

    private final @Nullable StigmergyWriter writer;
    private final @Nullable String agentId;

    public StigmergyWriteBackHandler(@Nullable StigmergyWriter writer, @Nullable String agentId) {
        this.writer = writer;
        this.agentId = agentId;
    }

    @Override
    public void accept(DecisionOutcome outcome) {
        if (writer == null) return;
        if (!(outcome instanceof DecisionOutcome.WriteBack wb)) return;

        for (var signal : wb.signals()) {
            writer.depositSignal(agentId != null ? agentId : "unknown", signal.name(), signal.strength());
        }
    }
}

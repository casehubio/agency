package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.SignalDeposit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StigmergyWriteBackHandlerTest {

    @Test
    void depositsSignalsViaWriter() {
        var deposited = new ArrayList<String>();
        StigmergyWriteBackHandler.StigmergyWriter writer =
                (agentId, name, strength) -> deposited.add(agentId + ":" + name + ":" + strength);

        var handler = new StigmergyWriteBackHandler(writer, "agent-1");
        handler.accept(new DecisionOutcome.WriteBack(
                List.of(),
                List.of(new SignalDeposit("need-capacity:analysis", 0.8, null)),
                List.of()));

        assertThat(deposited).containsExactly("agent-1:need-capacity:analysis:0.8");
    }

    @Test
    void depositsMultipleSignals() {
        var deposited = new ArrayList<String>();
        StigmergyWriteBackHandler.StigmergyWriter writer =
                (agentId, name, strength) -> deposited.add(name);

        var handler = new StigmergyWriteBackHandler(writer, "a");
        handler.accept(new DecisionOutcome.WriteBack(
                List.of(),
                List.of(new SignalDeposit("signal-a", 0.5, null),
                        new SignalDeposit("signal-b", 0.9, null)),
                List.of()));

        assertThat(deposited).containsExactly("signal-a", "signal-b");
    }

    @Test
    void noOpWhenWriterIsNull() {
        var handler = new StigmergyWriteBackHandler(null, null);
        handler.accept(new DecisionOutcome.WriteBack(
                List.of(),
                List.of(new SignalDeposit("test", 1.0, null)),
                List.of()));
    }

    @Test
    void ignoresNonWriteBackOutcomes() {
        var deposited = new ArrayList<String>();
        StigmergyWriteBackHandler.StigmergyWriter writer =
                (agentId, name, strength) -> deposited.add(name);

        var handler = new StigmergyWriteBackHandler(writer, "a");
        handler.accept(new DecisionOutcome.Composite(List.of()));

        assertThat(deposited).isEmpty();
    }
}

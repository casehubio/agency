package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.orchestration.listener.EventLogListener;
import io.casehub.agency.orchestration.listener.LedgerExecutionListener;
import io.casehub.agency.orchestration.listener.MetricsListener;
import io.casehub.agency.orchestration.execution.ExecutionEventListener;
import io.casehub.agency.core.yaml.spec.ExecutionListenerSpec;
public class ExecutionListenerRegistry {

    public ExecutionEventListener resolve(ExecutionListenerSpec spec,
                                           EventLogListener.EventSink eventSink,
                                           LedgerExecutionListener.LedgerSink ledgerSink,
                                           io.opentelemetry.api.metrics.Meter meter) {
        return switch (spec) {
            case ExecutionListenerSpec.EventLog ignored -> {
                if (eventSink == null)
                    throw new IllegalStateException("event-log listener requires EventSink");
                yield new EventLogListener(eventSink);
            }
            case ExecutionListenerSpec.Ledger l -> {
                if (ledgerSink == null)
                    throw new IllegalStateException("ledger listener requires LedgerSink");
                yield new LedgerExecutionListener(ledgerSink,
                        l.supervisorActorId() != null ? l.supervisorActorId() : "system");
            }
            case ExecutionListenerSpec.Checkpointing ignored ->
                throw new UnsupportedOperationException(
                        "checkpointing listener requires engine runtime context — "
                        + "resolved by PatternWorkerFunction, not the registry");
            case ExecutionListenerSpec.Metrics ignored -> {
                if (meter == null)
                    throw new IllegalStateException("metrics listener requires Meter");
                yield new MetricsListener(meter);
            }
        };
    }
}

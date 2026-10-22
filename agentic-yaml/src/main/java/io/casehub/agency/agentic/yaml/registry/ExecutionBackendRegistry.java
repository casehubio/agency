package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.model.ExecutionBackend;
import io.casehub.agency.agentic.yaml.spec.ExecutionBackendSpec;

public class ExecutionBackendRegistry {

    private final EventConcurrencyPolicyRegistry policyRegistry;

    public ExecutionBackendRegistry(EventConcurrencyPolicyRegistry policyRegistry) {
        this.policyRegistry = policyRegistry;
    }

    @SuppressWarnings("unchecked")
    public <T> ExecutionBackend<T> resolve(ExecutionBackendSpec spec) {
        return switch (spec) {
            case ExecutionBackendSpec.Reactive ignored -> ExecutionBackend.reactive();
            case ExecutionBackendSpec.EngineHosted ignored ->
                throw new UnsupportedOperationException(
                        "engine-hosted backend requires engine runtime — "
                        + "use PatternWorkerFunction via CaseDefinition YAML");
            case ExecutionBackendSpec.Choreographed c -> {
                var policy = policyRegistry.resolve(c.policy());
                yield ExecutionBackend.choreographed(policy);
            }
        };
    }
}

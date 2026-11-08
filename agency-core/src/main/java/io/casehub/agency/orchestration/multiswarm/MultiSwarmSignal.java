package io.casehub.agency.orchestration.multiswarm;

import io.casehub.api.model.stigmergy.SwarmProgress;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public sealed interface MultiSwarmSignal {
    UUID sourceSwarmId();
    Set<String> domainTags();
    long timestamp();

    record Finding(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                   String domain, String conclusion, double confidence,
                   Map<String, String> evidence) implements MultiSwarmSignal {
        public Finding {
            Objects.requireNonNull(sourceSwarmId);
            domainTags = Set.copyOf(domainTags);
            Objects.requireNonNull(domain);
            Objects.requireNonNull(conclusion);
            evidence = Map.copyOf(evidence);
        }
    }

    record Progress(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                    SwarmProgress progress) implements MultiSwarmSignal {
        public Progress {
            Objects.requireNonNull(sourceSwarmId);
            domainTags = Set.copyOf(domainTags);
            Objects.requireNonNull(progress);
        }
    }

    record ResourceClaim(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                         Set<String> agentIds, Set<String> capabilities) implements MultiSwarmSignal {
        public ResourceClaim {
            Objects.requireNonNull(sourceSwarmId);
            domainTags = Set.copyOf(domainTags);
            agentIds = Set.copyOf(agentIds);
            capabilities = Set.copyOf(capabilities);
        }
    }

    record Completion(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                      String domain, String resultSummary) implements MultiSwarmSignal {
        public Completion {
            Objects.requireNonNull(sourceSwarmId);
            domainTags = Set.copyOf(domainTags);
            Objects.requireNonNull(domain);
            Objects.requireNonNull(resultSummary);
        }
    }

    record Handoff(UUID sourceSwarmId, Set<String> domainTags, long timestamp,
                   String domain, Map<String, String> context,
                   Set<String> requiredCapabilities) implements MultiSwarmSignal {
        public Handoff {
            Objects.requireNonNull(sourceSwarmId);
            domainTags = Set.copyOf(domainTags);
            Objects.requireNonNull(domain);
            context = Map.copyOf(context);
            requiredCapabilities = Set.copyOf(requiredCapabilities);
        }
    }
}

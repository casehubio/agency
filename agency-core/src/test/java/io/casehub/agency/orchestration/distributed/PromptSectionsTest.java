package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.DecisionContext;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromptSectionsTest {

    private static final Map<String, AgentRole> ROLES = new LinkedHashMap<>(Map.of(
            "analyst", AgentRole.of("analysis", StructuralRole.PEER),
            "researcher", AgentRole.of("research", StructuralRole.PEER)));

    private static final DecisionContext CTX = new DecisionContext(
            UUID.randomUUID(), "analyst",
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            new TopologyDescriptor(TopologyType.DISTRIBUTED, ROLES));

    @Test
    void roleContextRendersAgentRoleAndPeers() {
        var section = new RoleContextSection(ROLES);
        var output = section.contribute(CTX);

        assertThat(output).contains("## Your Role");
        assertThat(output).contains("analyst");
        assertThat(output).contains("analysis");
        assertThat(output).contains("researcher");
    }

    @Test
    void roleContextReturnsEmptyForUnknownAgent() {
        var section = new RoleContextSection(ROLES);
        var ctx = new DecisionContext(UUID.randomUUID(), "unknown",
                DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
                new TopologyDescriptor(TopologyType.DISTRIBUTED, ROLES));

        assertThat(section.contribute(ctx)).isEmpty();
    }

    @Test
    void stigmergySignalRendersTable() {
        var signals = List.of(
                new VisibleSignal("need-capacity:analysis", 0.8, 3),
                new VisibleSignal("in-progress:task-1", 1.0, 1));
        var section = new StigmergySignalSection(() -> signals);

        var output = section.contribute(CTX);

        assertThat(output).contains("## Stigmergy Signals");
        assertThat(output).contains("need-capacity:analysis");
        assertThat(output).contains("0.80");
        assertThat(output).contains("in-progress:task-1");
    }

    @Test
    void stigmergySignalReturnsEmptyWhenNoSignals() {
        var section = new StigmergySignalSection(List::of);
        assertThat(section.contribute(CTX)).isEmpty();
    }

    @Test
    void peerStateRendersPeerStatesExcludingSelf() {
        var states = new LinkedHashMap<String, String>();
        states.put("analyst", "EXECUTING");
        states.put("researcher", "IDLE");
        var section = new PeerStateSection(() -> states);

        var output = section.contribute(CTX);

        assertThat(output).contains("## Peer States");
        assertThat(output).contains("researcher");
        assertThat(output).contains("IDLE");
        assertThat(output).doesNotContain("analyst**: EXECUTING");
    }

    @Test
    void peerStateReturnsEmptyWhenNoPeers() {
        var section = new PeerStateSection(Map::of);
        assertThat(section.contribute(CTX)).isEmpty();
    }

    @Test
    void swarmProgressRendersMetrics() {
        var progress = new SwarmProgress(0.5, 0.3, 0.4, Instant.now());
        var section = new SwarmProgressSection(() -> progress);

        var output = section.contribute(CTX);

        assertThat(output).contains("## Swarm Progress");
        assertThat(output).contains("Exploration: 50.0%");
        assertThat(output).contains("Consensus: 30.0%");
        assertThat(output).contains("Stability: 40.0%");
    }

    @Test
    void swarmProgressReturnsEmptyWhenNull() {
        var section = new SwarmProgressSection(() -> null);
        assertThat(section.contribute(CTX)).isEmpty();
    }
}

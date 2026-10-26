package io.casehub.agency.orchestration.swarm;

import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LlmSwarmCoordinatorTest {

    @Test
    void assemblesPromptFromSections() {
        var section1 = (SwarmPromptSection) ctx -> "Section 1 content";
        var section2 = (SwarmPromptSection) ctx -> "Section 2 content";

        var coordinator = new SwarmCoordinatorBuilder()
                .agentProvider(mock(AgentProvider.class))
                .promptSections(List.of(section1, section2))
                .build();

        var prompt = coordinator.assembleCoordinatorPrompt(coordinator.context());
        assertThat(prompt).contains("Section 1 content");
        assertThat(prompt).contains("Section 2 content");
    }

    @Test
    void builderWiresAllComponents() {
        var coordinator = new SwarmCoordinatorBuilder()
                .agentProvider(mock(AgentProvider.class))
                .experienceConfig(new ExperienceReplayConfig(10, 0.5))
                .invocationPolicy(SwarmInvocationPolicy.defaults())
                .preFilterThreshold(8)
                .convergenceThreshold(0.9)
                .build();

        assertThat(coordinator.routingStrategy()).isNotNull();
        assertThat(coordinator.aggregation()).isNotNull();
        assertThat(coordinator.convergenceTermination()).isNotNull();
        assertThat(coordinator.convergenceTermination().threshold()).isEqualTo(0.9);
        assertThat(coordinator.eventSource()).isNotNull();
        assertThat(coordinator.invocationTracker()).isNotNull();
        assertThat(coordinator.fallbackStrategy()).isNotNull();
        assertThat(coordinator.context()).isNotNull();
    }

    @Test
    void emptyPromptSectionsProduceEmptyPrompt() {
        var coordinator = new SwarmCoordinatorBuilder()
                .agentProvider(mock(AgentProvider.class))
                .build();

        var prompt = coordinator.assembleCoordinatorPrompt(coordinator.context());
        assertThat(prompt.trim()).isEmpty();
    }

    @Test
    void nullContributionSkipped() {
        var nullSection = (SwarmPromptSection) ctx -> null;
        var validSection = (SwarmPromptSection) ctx -> "Valid content";

        var coordinator = new SwarmCoordinatorBuilder()
                .agentProvider(mock(AgentProvider.class))
                .promptSections(List.of(nullSection, validSection))
                .build();

        var prompt = coordinator.assembleCoordinatorPrompt(coordinator.context());
        assertThat(prompt).contains("Valid content");
        assertThat(prompt).doesNotContain("null");
    }
}

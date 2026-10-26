package io.casehub.agency.orchestration.swarm;

import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmExperienceSectionTest {

    private static final SwarmProgress PROGRESS =
            new SwarmProgress(0.5, 0.3, 0.4, Instant.now());

    @Test
    void contribute_formatsExperiences() {
        var step = new ExperiencePlanStep("step1", "analysis", "analyst-1", RoutingOutcome.SUCCESS, 1, Map.of(), null, null);
        var experience = new RetrievedExperience(
                "problem", "solution", "SUCCESS", 0.9, 0.8,
                Map.of(), List.of(step), Map.of());

        var context = new SwarmContext();
        context.onSwarmInitialised(List.of(experience), PROGRESS);

        var section = new SwarmExperienceSection(ExperienceReplayConfig.defaults());
        var text = section.contribute(context);

        assertThat(text).contains("Historical Coordination Patterns");
        assertThat(text).contains("SUCCESS");
        assertThat(text).contains("analyst-1");
    }

    @Test
    void contribute_filtersLowSimilarity() {
        var experience = new RetrievedExperience(
                "problem", "solution", "SUCCESS", 0.9, 0.1,
                Map.of(), List.of(), Map.of());

        var context = new SwarmContext();
        context.onSwarmInitialised(List.of(experience), PROGRESS);

        var section = new SwarmExperienceSection(new ExperienceReplayConfig(5, 0.3));
        var text = section.contribute(context);

        assertThat(text).isEmpty();
    }

    @Test
    void contribute_limitsToMaxTraces() {
        var experiences = List.of(
                makeExperience(0.9), makeExperience(0.8), makeExperience(0.7));

        var context = new SwarmContext();
        context.onSwarmInitialised(experiences, PROGRESS);

        var section = new SwarmExperienceSection(new ExperienceReplayConfig(2, 0.3));
        var text = section.contribute(context);

        long expCount = text.lines().filter(l -> l.startsWith("### Experience")).count();
        assertThat(expCount).isEqualTo(2);
    }

    @Test
    void contribute_emptyWhenNoExperiences() {
        var context = new SwarmContext();
        var section = new SwarmExperienceSection(ExperienceReplayConfig.defaults());
        assertThat(section.contribute(context)).isEmpty();
    }

    private RetrievedExperience makeExperience(double similarity) {
        var step = new ExperiencePlanStep("s1", "cap", "worker", RoutingOutcome.SUCCESS, 1, Map.of(), null, null);
        return new RetrievedExperience(
                "problem", "solution", "SUCCESS", 0.9, similarity,
                Map.of(), List.of(step), Map.of());
    }
}

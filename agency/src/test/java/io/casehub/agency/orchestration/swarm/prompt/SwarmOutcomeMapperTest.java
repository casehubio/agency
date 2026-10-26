package io.casehub.agency.orchestration.swarm.prompt;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmOutcomeMapperTest {

    private final SwarmOutcomeMapper mapper = new SwarmOutcomeMapper("swarm-team-formation");

    @Test
    void mapsConvergedOutcome() {
        var result = mapper.map("v1", "CONVERGED", 0.85, Duration.ofSeconds(30));
        assertThat(result.variantId()).isEqualTo("v1");
        assertThat(result.signatureId()).isEqualTo("swarm-team-formation");
        assertThat(result.outcome()).isEqualTo("CONVERGED");
        assertThat(result.similarityScore()).isEqualTo(0.85);
        assertThat(result.executionDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(result.occurredAt()).isNotNull();
    }

    @Test
    void normalisesOutcomeToUpperCase() {
        var result = mapper.map("v1", "converged", 0.5, null);
        assertThat(result.outcome()).isEqualTo("CONVERGED");
    }

    @Test
    void normalisesBlankOutcomeToFailed() {
        var result = mapper.map("v1", "", 0.5, null);
        assertThat(result.outcome()).isEqualTo("FAILED");
    }

    @Test
    void normalisesNullOutcomeToFailed() {
        var result = mapper.map("v1", null, 0.5, null);
        assertThat(result.outcome()).isEqualTo("FAILED");
    }

    @Test
    void stripsWhitespace() {
        var result = mapper.map("v1", "  deadlock  ", 0.5, null);
        assertThat(result.outcome()).isEqualTo("DEADLOCK");
    }

    @Test
    void nullDuration_isPreserved() {
        var result = mapper.map("v1", "CONVERGED", 0.5, null);
        assertThat(result.executionDuration()).isNull();
    }
}

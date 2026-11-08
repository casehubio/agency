package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SwarmConclusionTest {

    @Test
    void creates_with_valid_fields() {
        var id = UUID.randomUUID();
        var c = new SwarmConclusion(id, "system-health", "node-7:degraded", 0.85);
        assertThat(c.swarmId()).isEqualTo(id);
        assertThat(c.domain()).isEqualTo("system-health");
        assertThat(c.conclusion()).isEqualTo("node-7:degraded");
        assertThat(c.confidence()).isEqualTo(0.85);
    }

    @Test
    void rejects_null_swarmId() {
        assertThatNullPointerException().isThrownBy(() ->
                new SwarmConclusion(null, "domain", "conclusion", 0.5));
    }

    @Test
    void rejects_null_domain() {
        assertThatNullPointerException().isThrownBy(() ->
                new SwarmConclusion(UUID.randomUUID(), null, "conclusion", 0.5));
    }

    @Test
    void rejects_null_conclusion() {
        assertThatNullPointerException().isThrownBy(() ->
                new SwarmConclusion(UUID.randomUUID(), "domain", null, 0.5));
    }

    @Test
    void rejects_confidence_below_zero() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SwarmConclusion(UUID.randomUUID(), "domain", "conclusion", -0.1));
    }

    @Test
    void rejects_confidence_above_one() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SwarmConclusion(UUID.randomUUID(), "domain", "conclusion", 1.1));
    }

    @Test
    void accepts_boundary_confidence_zero() {
        var c = new SwarmConclusion(UUID.randomUUID(), "domain", "conclusion", 0.0);
        assertThat(c.confidence()).isEqualTo(0.0);
    }

    @Test
    void accepts_boundary_confidence_one() {
        var c = new SwarmConclusion(UUID.randomUUID(), "domain", "conclusion", 1.0);
        assertThat(c.confidence()).isEqualTo(1.0);
    }
}

package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.FewShotExample;
import io.casehub.agency.optimisation.prompt.PromptVariant;
import io.casehub.agency.optimisation.prompt.PromptVariantStore;
import io.casehub.agency.optimisation.prompt.VariantSelector;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwarmOptimisedFewShotSectionTest {

    private PromptVariantStore store;
    private VariantSelector selector;
    private SwarmContext context;

    @BeforeEach
    void setUp() {
        store = mock(PromptVariantStore.class);
        selector = mock(VariantSelector.class);
        context = new SwarmContext();
        when(selector.selectSlot(any(), any())).thenReturn("control");
    }

    @Test
    void returnsNull_whenNoActiveVariant() {
        when(store.getActive(any(), any())).thenReturn(null);

        var section = new SwarmOptimisedFewShotSection(store, selector);
        assertThat(section.contribute(context)).isNull();
    }

    @Test
    void returnsNull_whenVariantHasNoExamples() {
        var variant = new PromptVariant(
                SwarmPromptSignatures.TEAM_FORMATION.id(), "v1",
                List.of(), null, 0.8, Instant.now(), null, 0);
        when(store.getActive(any(), any())).thenReturn(variant);

        var section = new SwarmOptimisedFewShotSection(store, selector);
        assertThat(section.contribute(context)).isNull();
    }

    @Test
    void rendersExamples_whenVariantHasExamples() {
        var examples = List.of(
                new FewShotExample(
                        "Security analysis task with 5 candidates",
                        "Selected analyst + scanner, team: {security: [analyst, scanner]}",
                        "CONVERGED", 0.9, null),
                new FewShotExample(
                        "Data pipeline task with 3 candidates",
                        "Selected all, team: {etl: [extractor, transformer, loader]}",
                        "CONSENSUS", 0.85, "Fast convergence"));
        var variant = new PromptVariant(
                SwarmPromptSignatures.TEAM_FORMATION.id(), "v1",
                examples, null, 0.8, Instant.now(), null, 0);
        when(store.getActive(eq(SwarmPromptSignatures.TEAM_FORMATION.id()), eq("control")))
                .thenReturn(variant);

        var section = new SwarmOptimisedFewShotSection(store, selector);
        var result = section.contribute(context);

        assertThat(result).isNotNull();
        assertThat(result).contains("Successful Coordination Examples");
        assertThat(result).contains("Security analysis task");
        assertThat(result).contains("Team decision:");
        assertThat(result).contains("Outcome: CONVERGED");
        assertThat(result).contains("Note: Fast convergence");
    }

    @Test
    void usesCorrectSignatureId() {
        when(store.getActive(eq("swarm-team-formation"), any())).thenReturn(null);

        var section = new SwarmOptimisedFewShotSection(store, selector);
        section.contribute(context);

        // Default constructor uses TEAM_FORMATION signature
        org.mockito.Mockito.verify(store).getActive(eq("swarm-team-formation"), eq("control"));
    }

    @Test
    void customSignatureId_isUsed() {
        when(store.getActive(eq("custom-sig"), any())).thenReturn(null);

        var section = new SwarmOptimisedFewShotSection(store, selector, "custom-sig");
        section.contribute(context);

        org.mockito.Mockito.verify(store).getActive(eq("custom-sig"), eq("control"));
    }
}

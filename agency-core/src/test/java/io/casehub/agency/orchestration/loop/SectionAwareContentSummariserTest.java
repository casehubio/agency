package io.casehub.agency.orchestration.loop;

import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SectionAwareContentSummariserTest {

    static final DecisionContext CTX = new DecisionContext(
        UUID.randomUUID(), "agent-1", DecisionScope.FULL_AUTONOMY,
        CommunicationMode.STIGMERGY,
        new TopologyDescriptor(TopologyType.CENTRALISED, Map.of()));

    @Test
    void delegatesToWrappedSummariser() {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        ContentSummariser<ObservationEvent, DecisionOutcome> delegate =
            (items, prev) -> CompletableFuture.completedFuture(outcome);

        var summariser = new SectionAwareContentSummariser(
            delegate, List.of(), CTX);

        var events = List.of(ObservationEvent.of("key", "value"));
        var result = summariser.summarise(events, null)
            .toCompletableFuture().join();
        assertThat(result).isSameAs(outcome);
    }

    @Test
    void callsSectionsWithContext() {
        var captured = new AtomicReference<DecisionContext>();
        LoopPromptSection section = ctx -> {
            captured.set(ctx);
            return "section-output";
        };

        ContentSummariser<ObservationEvent, DecisionOutcome> delegate =
            (items, prev) -> CompletableFuture.completedFuture(
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));

        var summariser = new SectionAwareContentSummariser(
            delegate, List.of(section), CTX);

        summariser.summarise(List.of(ObservationEvent.of("k", "v")), null)
            .toCompletableFuture().join();
        assertThat(captured.get()).isSameAs(CTX);
    }

    @Test
    void skipsEmptySectionContributions() {
        LoopPromptSection empty = ctx -> "";
        LoopPromptSection nullSection = ctx -> null;
        LoopPromptSection real = ctx -> "content";

        ContentSummariser<ObservationEvent, DecisionOutcome> delegate =
            (items, prev) -> CompletableFuture.completedFuture(
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));

        var summariser = new SectionAwareContentSummariser(
            delegate, List.of(empty, nullSection, real), CTX);

        var result = summariser.summarise(
            List.of(ObservationEvent.of("k", "v")), null)
            .toCompletableFuture().join();
        assertThat(result).isNotNull();
    }

    @Test
    void assembledPromptContext_buildsFromSections() {
        LoopPromptSection a = ctx -> "section-A";
        LoopPromptSection b = ctx -> "section-B";
        LoopPromptSection empty = ctx -> "";

        ContentSummariser<ObservationEvent, DecisionOutcome> delegate =
            (items, prev) -> CompletableFuture.completedFuture(
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));

        var summariser = new SectionAwareContentSummariser(
            delegate, List.of(a, empty, b), CTX);

        assertThat(summariser.assembledPromptContext())
            .isEqualTo("section-A\nsection-B\n");
    }

    @Test
    void summarise_prependsPromptContextAsObservation() {
        LoopPromptSection section       = ctx -> "System instruction: coordinate agents";
        var               capturedItems = new AtomicReference<List<ObservationEvent>>();
        ContentSummariser<ObservationEvent, DecisionOutcome> delegate = (items, prev) -> {
            capturedItems.set(items);
            return CompletableFuture.completedFuture(
                    new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));
        };

        var summariser = new SectionAwareContentSummariser(delegate, List.of(section), CTX);
        var event      = ObservationEvent.of("test", "value");
        summariser.summarise(List.of(event), null).toCompletableFuture().join();

        assertThat(capturedItems.get()).hasSizeGreaterThan(1);
        var promptEvent = capturedItems.get().stream()
                                       .filter(e -> "prompt-context".equals(e.key()))
                                       .findFirst();
        assertThat(promptEvent).isPresent();
        assertThat((String) promptEvent.get().value())
                .contains("System instruction: coordinate agents");
    }

}

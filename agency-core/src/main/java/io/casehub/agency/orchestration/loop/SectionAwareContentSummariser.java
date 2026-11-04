package io.casehub.agency.orchestration.loop;

import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletionStage;

class SectionAwareContentSummariser
        implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final ContentSummariser<ObservationEvent, DecisionOutcome> delegate;
    private final List<LoopPromptSection> sections;
    private final DecisionContext context;

    SectionAwareContentSummariser(
            ContentSummariser<ObservationEvent, DecisionOutcome> delegate,
            List<LoopPromptSection> sections,
            DecisionContext context) {
        this.delegate = delegate;
        this.sections = List.copyOf(sections);
        this.context = context;
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(
            List<ObservationEvent> items, @Nullable DecisionOutcome previous) {
        var promptContext = new StringBuilder();
        for (var section : sections) {
            var contribution = section.contribute(context);
            if (contribution != null && !contribution.isEmpty()) {
                promptContext.append(contribution).append("\n");
            }
        }
        return delegate.summarise(items, previous);
    }

    String assembledPromptContext() {
        var sb = new StringBuilder();
        for (var section : sections) {
            var contribution = section.contribute(context);
            if (contribution != null && !contribution.isEmpty()) {
                sb.append(contribution).append("\n");
            }
        }
        return sb.toString();
    }
}

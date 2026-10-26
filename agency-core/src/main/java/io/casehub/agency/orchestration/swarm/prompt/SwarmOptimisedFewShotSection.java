package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.FewShotExample;
import io.casehub.agency.optimisation.prompt.PromptVariantStore;
import io.casehub.agency.optimisation.prompt.VariantSelector;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.swarm.SwarmPromptSection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class SwarmOptimisedFewShotSection implements SwarmPromptSection {

    private final PromptVariantStore store;
    private final VariantSelector    selector;
    private final String             signatureId;

    public SwarmOptimisedFewShotSection(PromptVariantStore store,
                                        VariantSelector selector,
                                        String signatureId) {
        this.store       = store;
        this.selector    = selector;
        this.signatureId = signatureId;
    }

    public SwarmOptimisedFewShotSection(PromptVariantStore store, VariantSelector selector) {
        this(store, selector, SwarmPromptSignatures.TEAM_FORMATION.id());
    }

    @Override
    public @Nullable String contribute(SwarmContext context) {
        var slot    = selector.selectSlot(executionId(context), signatureId);
        var variant = store.getActive(signatureId, slot);
        if (variant == null || variant.examples().isEmpty()) {
            return null;
        }
        return formatExamples(variant.examples());
    }

    private String formatExamples(List<FewShotExample> examples) {
        var sb = new StringBuilder("## Successful Coordination Examples\n\n");
        for (int i = 0; i < examples.size(); i++) {
            var ex = examples.get(i);
            sb.append(i + 1).append(". ").append(ex.input())
              .append("\n   Team decision: ").append(ex.output())
              .append("\n   Outcome: ").append(ex.outcome());
            if (ex.annotation() != null) {
                sb.append("\n   Note: ").append(ex.annotation());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private UUID executionId(SwarmContext context) {
        return UUID.nameUUIDFromBytes(
                String.valueOf(System.identityHashCode(context)).getBytes());
    }
}

package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.api.model.stigmergy.CategoryDescriptor;
import io.casehub.api.model.stigmergy.StageDescriptor;
import io.casehub.api.spi.improvement.ImprovementCategoryProvider;

import java.util.List;

public class SwarmPromptCategoryProvider implements ImprovementCategoryProvider {

    static final String DOMAIN_ID = "swarm-prompt-optimisation";
    static final String CATEGORY_ID = "swarm-prompt-tuning";

    private static final List<CategoryDescriptor> CATEGORIES = List.of(
            new CategoryDescriptor(CATEGORY_ID, "Swarm Prompt Tuning",
                    "Swarm coordinator prompt variant optimisation via few-shot curation and instruction refinement",
                    DOMAIN_ID));

    private static final List<StageDescriptor> STAGES = List.of(
            new StageDescriptor("evaluate", "Evaluate", 0, false, DOMAIN_ID),
            new StageDescriptor("optimise", "Optimise", 1, false, DOMAIN_ID),
            new StageDescriptor("promote", "Promote", 2, false, DOMAIN_ID),
            new StageDescriptor("outcome-recording", "Outcome Recording", 3, false, DOMAIN_ID));

    @Override
    public String domainId() {
        return DOMAIN_ID;
    }

    @Override
    public List<CategoryDescriptor> categories() {
        return CATEGORIES;
    }

    @Override
    public List<StageDescriptor> stages() {
        return STAGES;
    }
}

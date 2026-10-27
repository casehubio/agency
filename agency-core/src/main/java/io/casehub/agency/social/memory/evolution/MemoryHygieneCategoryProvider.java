package io.casehub.agency.social.memory.evolution;

import io.casehub.api.model.stigmergy.CategoryDescriptor;
import io.casehub.api.model.stigmergy.StageDescriptor;
import io.casehub.api.spi.improvement.ImprovementCategoryProvider;
import java.util.List;

public class MemoryHygieneCategoryProvider implements ImprovementCategoryProvider {

    static final String DOMAIN_ID = "memory-hygiene";
    static final String CATEGORY_ID = "memory-hygiene";

    private static final List<CategoryDescriptor> CATEGORIES = List.of(
            new CategoryDescriptor(CATEGORY_ID, "Memory Hygiene Tuning",
                    "Agent memory retention, consolidation, and confidence scoring parameter tuning",
                    DOMAIN_ID));

    private static final List<StageDescriptor> STAGES = List.of(
            new StageDescriptor("evaluate", "Evaluate", 0, false, DOMAIN_ID),
            new StageDescriptor("tune", "Tune", 1, false, DOMAIN_ID),
            new StageDescriptor("verify", "Verify", 2, false, DOMAIN_ID));

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

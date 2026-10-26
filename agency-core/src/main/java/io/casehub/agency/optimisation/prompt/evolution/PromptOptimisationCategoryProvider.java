/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.agency.optimisation.prompt.evolution;

import io.casehub.api.model.stigmergy.CategoryDescriptor;
import io.casehub.api.model.stigmergy.StageDescriptor;
import io.casehub.api.spi.improvement.ImprovementCategoryProvider;
import java.util.List;

public class PromptOptimisationCategoryProvider implements ImprovementCategoryProvider {

    static final String DOMAIN_ID = "prompt-optimisation";
    static final String CATEGORY_ID = "prompt-tuning";

    private static final List<CategoryDescriptor> CATEGORIES = List.of(
            new CategoryDescriptor(CATEGORY_ID, "Prompt Tuning",
                    "LLM prompt variant optimisation via few-shot curation and instruction refinement",
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

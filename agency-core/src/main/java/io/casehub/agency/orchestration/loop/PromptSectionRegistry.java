package io.casehub.agency.orchestration.loop;

import java.util.List;

public interface PromptSectionRegistry {

    LoopPromptSection resolve(String name);

    default List<LoopPromptSection> resolveAll(List<String> names) {
        return names.stream().map(this::resolve).toList();
    }
}

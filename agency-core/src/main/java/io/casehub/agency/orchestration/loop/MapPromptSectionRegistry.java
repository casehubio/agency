package io.casehub.agency.orchestration.loop;

import java.util.Map;

public class MapPromptSectionRegistry implements PromptSectionRegistry {

    private final Map<String, LoopPromptSection> sections;

    public MapPromptSectionRegistry(Map<String, LoopPromptSection> sections) {
        this.sections = Map.copyOf(sections);
    }

    @Override
    public LoopPromptSection resolve(String name) {
        var section = sections.get(name);
        if (section == null) {
            throw new IllegalArgumentException(
                "Unknown prompt section: " + name
                + " — registered: " + sections.keySet());
        }
        return section;
    }
}

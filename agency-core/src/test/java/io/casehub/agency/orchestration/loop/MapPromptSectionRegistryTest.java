package io.casehub.agency.orchestration.loop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MapPromptSectionRegistryTest {

    @Test
    void resolve_knownName() {
        LoopPromptSection section = ctx -> "hello";
        var registry = new MapPromptSectionRegistry(Map.of("greeting", section));
        assertThat(registry.resolve("greeting")).isSameAs(section);
    }

    @Test
    void resolve_unknownName_throws() {
        var registry = new MapPromptSectionRegistry(Map.of());
        assertThatIllegalArgumentException()
            .isThrownBy(() -> registry.resolve("missing"))
            .withMessageContaining("Unknown prompt section: missing");
    }

    @Test
    void resolveAll_preservesOrder() {
        LoopPromptSection a = ctx -> "a";
        LoopPromptSection b = ctx -> "b";
        var registry = new MapPromptSectionRegistry(Map.of("a", a, "b", b));
        var resolved = registry.resolveAll(List.of("b", "a"));
        assertThat(resolved).containsExactly(b, a);
    }

    @Test
    void resolve_unknownName_showsRegistered() {
        LoopPromptSection s = ctx -> "x";
        var registry = new MapPromptSectionRegistry(Map.of("known", s));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> registry.resolve("unknown"))
            .withMessageContaining("known");
    }
}

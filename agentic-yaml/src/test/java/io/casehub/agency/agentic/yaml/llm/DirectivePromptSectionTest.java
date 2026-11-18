package io.casehub.agency.core.yaml.llm;

import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectivePromptSectionTest {

    @Test
    void wrappedSectionsHaveDistinctNames() {
        CognitionPromptRenderer mood = ctx -> "mood content";
        CognitionPromptRenderer drives = ctx -> "drive content";
        CognitionPromptRenderer narrative = ctx -> "narrative content";

        var wrapped = List.of(
                DirectivePromptSection.wrap(mood, "MoodPromptSection"),
                DirectivePromptSection.wrap(drives, "DrivePromptSection"),
                DirectivePromptSection.wrap(narrative, "NarrativePromptSection"));

        var ctx = new CognitionRenderContext("agent", "tenant", "subject");
        var map = new LinkedHashMap<String, String>();
        for (var section : wrapped) {
            var text = section.render(ctx);
            if (text != null && !text.isBlank()) {
                var dps = (DirectivePromptSection) section;
                map.put(dps.delegateName(), text);
            }
        }

        assertThat(map).hasSize(3);
        assertThat(map).containsKeys(
                "MoodPromptSection", "DrivePromptSection", "NarrativePromptSection");
    }

    @Test
    void mapKeyCollisionWithGetClassSimpleName() {
        CognitionPromptRenderer mood = ctx -> "mood content";
        CognitionPromptRenderer drives = ctx -> "drive content";

        var wrapped = List.of(
                DirectivePromptSection.wrap(mood, "MoodPromptSection"),
                DirectivePromptSection.wrap(drives, "DrivePromptSection"));

        var ctx = new CognitionRenderContext("agent", "tenant", "subject");
        var collisionMap = new LinkedHashMap<String, String>();
        for (var section : wrapped) {
            var text = section.render(ctx);
            if (text != null && !text.isBlank()) {
                collisionMap.put(section.getClass().getSimpleName(), text);
            }
        }

        // Demonstrates the bug: getClass().getSimpleName() returns
        // "DirectivePromptSection" for all, so map collapses to 1 entry
        assertThat(collisionMap).hasSize(1);
        assertThat(collisionMap).containsKey("DirectivePromptSection");
    }

    @Test
    void nullDelegateContentReturnsNull() {
        CognitionPromptRenderer empty = ctx -> null;
        var wrapped = DirectivePromptSection.wrap(empty, "EmptySection");

        var ctx = new CognitionRenderContext("agent", "tenant", "subject");
        assertThat(wrapped.render(ctx)).isNull();
    }

    @Test
    void blankDelegateContentReturnsNull() {
        CognitionPromptRenderer blank = ctx -> "   ";
        var wrapped = DirectivePromptSection.wrap(blank, "BlankSection");

        var ctx = new CognitionRenderContext("agent", "tenant", "subject");
        assertThat(wrapped.render(ctx)).isNull();
    }

    @Test
    void wrapWithoutNameUsesClassSimpleName() {
        var section = new NamedTestSection();
        var wrapped = (DirectivePromptSection) DirectivePromptSection.wrap(section);

        assertThat(wrapped.delegateName()).isEqualTo("NamedTestSection");
    }

    private static class NamedTestSection implements CognitionPromptRenderer {
        @Override
        public String render(CognitionRenderContext context) {
            return "test";
        }
    }
}

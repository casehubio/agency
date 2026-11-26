package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.TextFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextFilterRegistryTest {

    private static TextFilter identity() {
        return new TextFilter() {
            @Override public String apply(String t) { return t; }
            @Override public String name() { return "identity"; }
            @Override public int destructiveness() { return 0; }
        };
    }

    @Test
    void createRegisteredFilter() {
        var registry = new TextFilterRegistry();
        registry.register("test-filter", config -> identity());
        var filter = registry.create("test-filter", Map.of());
        assertThat(filter.apply("hello")).isEqualTo("hello");
    }

    @Test
    void createUnknownFilterThrows() {
        var registry = new TextFilterRegistry();
        assertThatThrownBy(() -> registry.create("no-such-filter", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-filter");
    }

    @Test
    void configPassedToFactory() {
        var registry = new TextFilterRegistry();
        registry.register("suffix", config -> {
            String suffix = (String) config.get("suffix");
            return new TextFilter() {
                @Override public String apply(String t) { return t + suffix; }
                @Override public String name() { return "suffix"; }
                @Override public int destructiveness() { return 0; }
            };
        });
        var filter = registry.create("suffix", Map.of("suffix", "!"));
        assertThat(filter.apply("hello")).isEqualTo("hello!");
    }

    @Test
    void hasTypeReturnsCorrectly() {
        var registry = new TextFilterRegistry();
        registry.register("exists", config -> identity());
        assertThat(registry.hasType("exists")).isTrue();
        assertThat(registry.hasType("missing")).isFalse();
    }
}

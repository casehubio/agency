package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.TextFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterChainTest {

    private static TextFilter append(String suffix) {
        return new TextFilter() {
            @Override public String apply(String text) { return text + suffix; }
            @Override public String name() { return "append-" + suffix; }
            @Override public int destructiveness() { return 0; }
        };
    }

    @Test
    void emptyChainReturnsInputUnchanged() {
        assertThat(FilterChain.EMPTY.apply("hello")).isEqualTo("hello");
    }

    @Test
    void singleFilterApplied() {
        var chain = FilterChain.of(append("!"));
        assertThat(chain.apply("hello")).isEqualTo("hello!");
    }

    @Test
    void multipleFiltersApplyInInsertionOrder() {
        var chain = FilterChain.of(append("-A"), append("-B"), append("-C"));
        assertThat(chain.apply("x")).isEqualTo("x-A-B-C");
    }

    @Test
    void orderPreservedRegardlessOfDestructiveness() {
        TextFilter high = new TextFilter() {
            @Override public String apply(String t) { return t + "-HIGH"; }
            @Override public String name() { return "high"; }
            @Override public int destructiveness() { return 10; }
        };
        TextFilter low = new TextFilter() {
            @Override public String apply(String t) { return t + "-LOW"; }
            @Override public String name() { return "low"; }
            @Override public int destructiveness() { return 0; }
        };
        var chain = FilterChain.of(high, low);
        assertThat(chain.apply("x")).isEqualTo("x-HIGH-LOW");
    }

    @Test
    void ofWithCleanupConfigDelegates() {
        var cleanup = CleanupConfig.of(append("!"));
        var chain = FilterChain.of(cleanup);
        assertThat(chain.apply("hello")).isEqualTo("hello!");
    }
}

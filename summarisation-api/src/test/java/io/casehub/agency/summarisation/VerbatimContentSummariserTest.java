package io.casehub.agency.summarisation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerbatimContentSummariserTest {

    private final VerbatimContentSummariser<String> summariser =
            new VerbatimContentSummariser<>(s -> s);

    @Test
    void rendersItemsAsBulletList() {
        var result = summariser.summarise(List.of("alpha", "beta"), null)
                .toCompletableFuture().join();
        assertThat(result).isEqualTo("- alpha\n- beta");
    }

    @Test
    void preservesPreviousText() {
        var result = summariser.summarise(List.of("new item"), "existing content")
                .toCompletableFuture().join();
        assertThat(result).startsWith("existing content\n\n");
        assertThat(result).contains("- new item");
    }

    @Test
    void nullPrevious_noPreamble() {
        var result = summariser.summarise(List.of("only"), null)
                .toCompletableFuture().join();
        assertThat(result).isEqualTo("- only");
    }

    @Test
    void emptyItems_producesEmptyResult() {
        var result = summariser.summarise(List.of(), null)
                .toCompletableFuture().join();
        assertThat(result).isEmpty();
    }
}

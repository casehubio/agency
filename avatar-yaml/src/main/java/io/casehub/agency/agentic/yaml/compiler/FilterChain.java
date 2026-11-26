package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.TextFilter;

public record FilterChain(TextFilter[] filters) {

    public static final FilterChain EMPTY = new FilterChain(new TextFilter[0]);

    public String apply(String text) {
        for (TextFilter filter : filters) {
            text = filter.apply(text);
        }
        return text;
    }

    public static FilterChain of(TextFilter... filters) {
        return filters.length == 0 ? EMPTY : new FilterChain(filters.clone());
    }

    public static FilterChain of(CleanupConfig cleanup) {
        return new FilterChain(cleanup.filters().toArray(TextFilter[]::new));
    }
}

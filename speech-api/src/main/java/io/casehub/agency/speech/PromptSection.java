package io.casehub.agency.speech;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface PromptSection {
    @Nullable String contribute(PromptContext context);
}

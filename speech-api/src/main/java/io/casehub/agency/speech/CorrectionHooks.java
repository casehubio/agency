package io.casehub.agency.speech;

import java.util.function.Consumer;
import java.util.function.Supplier;

public record CorrectionHooks(
        Consumer<String> onResponse,
        Supplier<String> vocabularyHintSupplier
) {}

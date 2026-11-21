package io.casehub.agency.core.yaml.spec;

import java.util.List;
import java.util.Objects;

public record TtsSpec(List<TtsEngineSpec> engines) {

    public TtsSpec {
        Objects.requireNonNull(engines, "engines");
        engines = List.copyOf(engines);
    }
}

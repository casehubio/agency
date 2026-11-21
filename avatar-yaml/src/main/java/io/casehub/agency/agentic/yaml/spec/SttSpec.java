package io.casehub.agency.core.yaml.spec;

import java.util.Objects;

public record SttSpec(String type) {

    public SttSpec {
        Objects.requireNonNull(type, "type");
    }
}

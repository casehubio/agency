package io.casehub.agency.core.yaml.spec.world;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record AffordanceSpec(
        @JsonAlias("action-type") String actionType,
        @Nullable String label,
        @JsonAlias("required-item") @Nullable String requiredItem,
        @JsonAlias("accepts-items") @Nullable List<String> acceptsItems) {}

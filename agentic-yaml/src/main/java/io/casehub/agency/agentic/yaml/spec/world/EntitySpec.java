package io.casehub.agency.core.yaml.spec.world;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record EntitySpec(
        @JsonAlias("display-name") String displayName,
        @Nullable String description,
        @Nullable List<AffordanceSpec> affordances) {}

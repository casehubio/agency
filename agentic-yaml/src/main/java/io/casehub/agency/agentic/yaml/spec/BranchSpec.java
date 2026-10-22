package io.casehub.agency.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

public record BranchSpec(String condition, AgentRefSpec agent,
                         @Nullable String description) {}

package io.casehub.agency.core.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public record NegotiationSpec(
        Set<String> parties,
        AcceptancePolicySpec acceptance,
        @Nullable List<TerminationSpec> termination
) {}

package io.casehub.agency.agentic.yaml.compiler;

import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.negotiation.NegotiationProjection;
import io.casehub.agency.negotiation.NegotiationState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public record CompiledNegotiation(
        NegotiationProjection projection,
        @Nullable TerminationCondition<NegotiationState> termination,
        Set<String> parties) {}

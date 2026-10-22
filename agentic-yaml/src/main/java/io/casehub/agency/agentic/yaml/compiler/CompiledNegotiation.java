package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.deliberation.negotiation.NegotiationProjection;
import io.casehub.agency.deliberation.negotiation.NegotiationState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public record CompiledNegotiation(
        NegotiationProjection projection,
        @Nullable TerminationCondition<NegotiationState> termination,
        Set<String> parties) {}

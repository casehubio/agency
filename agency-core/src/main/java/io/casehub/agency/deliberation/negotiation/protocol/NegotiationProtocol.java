package io.casehub.agency.deliberation.negotiation.protocol;

import io.casehub.agency.deliberation.negotiation.AcceptancePolicy;
import io.casehub.agency.deliberation.negotiation.NegotiationProjection;
import io.casehub.agency.deliberation.negotiation.NegotiationState;
import io.casehub.qhorus.api.channel.ChannelProjector;
import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProjectionResult;
import io.casehub.qhorus.api.spi.ProtocolContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class NegotiationProtocol implements ChannelProtocol {

    private final ChannelProjector projector;
    private final NegotiationRules defaultRules;
    private final boolean cachingEnabled;
    private final ConcurrentHashMap<UUID, CachedProjection> cache;

    @Inject
    public NegotiationProtocol(ChannelProjector projector) {
        this(projector, NegotiationRules.defaults(), true);
    }

    public NegotiationProtocol(ChannelProjector projector,
                               NegotiationRules defaultRules,
                               boolean cachingEnabled) {
        this.projector = Objects.requireNonNull(projector);
        this.defaultRules = Objects.requireNonNull(defaultRules);
        this.cachingEnabled = cachingEnabled;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String protocolName() {
        return "NEGOTIATION";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        NegotiationRules rules = defaultRules.withOverrides(ctx.policyOverrides());
        Set<String> parties = new HashSet<>(ctx.protocolParticipants());
        AcceptancePolicy policy = rules.resolveAcceptancePolicy(parties.size());

        NegotiationState state = projectState(ctx.channelId(), parties, policy);

        if (state.outcome().isTerminal()) {
            evictCache(ctx.channelId());
        }

        return rules.evaluate(state, ctx);
    }

    private NegotiationState projectState(UUID channelId, Set<String> parties,
                                          AcceptancePolicy policy) {
        NegotiationProjection projection = new NegotiationProjection(parties, policy);

        if (!cachingEnabled) {
            return projector.project(channelId, projection).state();
        }

        CachedProjection cached = cache.get(channelId);
        ProjectionResult<NegotiationState> result;

        if (cached != null && cached.matches(parties)) {
            result = projector.project(channelId, projection, cached.result());
        } else {
            result = projector.project(channelId, projection);
        }

        cache.put(channelId, new CachedProjection(result, parties));
        return result.state();
    }

    private void evictCache(UUID channelId) {
        cache.remove(channelId);
    }

    record CachedProjection(ProjectionResult<NegotiationState> result, Set<String> parties) {
        boolean matches(Set<String> currentParties) {
            return parties.equals(currentParties);
        }
    }
}

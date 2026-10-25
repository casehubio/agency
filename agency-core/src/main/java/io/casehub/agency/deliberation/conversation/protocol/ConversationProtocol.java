package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.ConversationState;
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
public class ConversationProtocol implements ChannelProtocol {

    private final ChannelProjector projector;
    private final ConversationRules defaultRules;
    private final boolean cachingEnabled;
    private final ConcurrentHashMap<UUID, CachedProjection> cache;

    @Inject
    public ConversationProtocol(ChannelProjector projector) {
        this(projector, ConversationRules.defaults(), true);
    }

    public ConversationProtocol(ChannelProjector projector,
                                ConversationRules defaultRules,
                                boolean cachingEnabled) {
        this.projector = Objects.requireNonNull(projector);
        this.defaultRules = Objects.requireNonNull(defaultRules);
        this.cachingEnabled = cachingEnabled;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String protocolName() {
        return "CONVERSATION";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        ConversationRules rules = defaultRules.withOverrides(ctx.policyOverrides());
        Set<String> participants = new HashSet<>(ctx.protocolParticipants());

        ConversationState state = projectState(ctx.channelId(), participants);

        return rules.evaluate(state, ctx);
    }

    private ConversationState projectState(UUID channelId, Set<String> participants) {
        DefaultConversationProjection projection = new DefaultConversationProjection();

        if (!cachingEnabled) {
            return projector.project(channelId, projection).state();
        }

        CachedProjection cached = cache.get(channelId);
        ProjectionResult<ConversationState> result;

        if (cached != null && cached.matches(participants)) {
            result = projector.project(channelId, projection, cached.result());
        } else {
            result = projector.project(channelId, projection);
        }

        cache.put(channelId, new CachedProjection(result, participants));
        return result.state();
    }

    record CachedProjection(ProjectionResult<ConversationState> result,
                            Set<String> participants) {
        boolean matches(Set<String> currentParticipants) {
            return participants.equals(currentParticipants);
        }
    }
}

package io.casehub.agency.deliberation.judgment.protocol;

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
public class JudgmentProtocol implements ChannelProtocol {

    private final ChannelProjector projector;
    private final JudgmentRules defaultRules;
    private final boolean cachingEnabled;
    private final ConcurrentHashMap<UUID, CachedProjection> cache;

    @Inject
    public JudgmentProtocol(ChannelProjector projector) {
        this(projector, JudgmentRules.defaults(), true);
    }

    public JudgmentProtocol(ChannelProjector projector,
                            JudgmentRules defaultRules,
                            boolean cachingEnabled) {
        this.projector = Objects.requireNonNull(projector);
        this.defaultRules = Objects.requireNonNull(defaultRules);
        this.cachingEnabled = cachingEnabled;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String protocolName() {
        return "JUDGMENT";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        JudgmentRules rules = defaultRules.withOverrides(ctx.policyOverrides());
        Set<String> reviewers = new HashSet<>(ctx.protocolParticipants());

        JudgmentState state = projectState(ctx.channelId(), reviewers);

        if (state.outcome().isTerminal()) {
            evictCache(ctx.channelId());
        }

        return rules.evaluate(state, ctx);
    }

    private JudgmentState projectState(UUID channelId, Set<String> reviewers) {
        JudgmentProjection projection = new JudgmentProjection(reviewers);

        if (!cachingEnabled) {
            return projector.project(channelId, projection).state();
        }

        CachedProjection cached = cache.get(channelId);
        ProjectionResult<JudgmentState> result;

        if (cached != null && cached.matches(reviewers)) {
            result = projector.project(channelId, projection, cached.result());
        } else {
            result = projector.project(channelId, projection);
        }

        cache.put(channelId, new CachedProjection(result, reviewers));
        return result.state();
    }

    private void evictCache(UUID channelId) {
        cache.remove(channelId);
    }

    record CachedProjection(ProjectionResult<JudgmentState> result,
                            Set<String> reviewers) {
        boolean matches(Set<String> currentReviewers) {
            return reviewers.equals(currentReviewers);
        }
    }
}

package io.casehub.agency.deliberation.negotiation.protocol;

import io.casehub.agency.deliberation.protocol.ProtocolStateEvaluator;
import io.casehub.qhorus.api.channel.ChannelProjector;
import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class NegotiationProtocol implements ChannelProtocol {

    private static final Logger LOG               = Logger.getLogger(NegotiationProtocol.class);
    private static final String STATE_BACKING_KEY = "negotiation.state-backing";
    private static final String PLAYBOOK_MODE     = "playbook";
    private static final String PROJECTION_MODE   = "projection";

    private final ProtocolStateEvaluator projectionEvaluator;
    private final ProtocolStateEvaluator playbookEvaluator;

    @Inject
    public NegotiationProtocol(ChannelProjector projector) {
        this.projectionEvaluator = new NegotiationProjectionEvaluator(
                projector, NegotiationRules.defaults(), true);
        this.playbookEvaluator   = null;
    }

    public NegotiationProtocol(ChannelProjector projector,
                               NegotiationRules defaultRules,
                               boolean cachingEnabled) {
        this.projectionEvaluator = new NegotiationProjectionEvaluator(
                projector, defaultRules, cachingEnabled);
        this.playbookEvaluator   = null;
    }

    public NegotiationProtocol(ProtocolStateEvaluator projectionEvaluator,
                               ProtocolStateEvaluator playbookEvaluator) {
        this.projectionEvaluator = Objects.requireNonNull(projectionEvaluator);
        this.playbookEvaluator   = playbookEvaluator;
    }

    @Override
    public String protocolName() {
        return "NEGOTIATION";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        String backing = ctx.policyOverrides().getOrDefault(STATE_BACKING_KEY, PROJECTION_MODE);

        if (PLAYBOOK_MODE.equals(backing)) {
            if (playbookEvaluator != null) {
                return playbookEvaluator.evaluate(ctx);
            }
            LOG.warnf("Playbook mode requested but no ProtocolStateEvaluator available — falling back to projection");
        } else if (!PROJECTION_MODE.equals(backing)) {
            LOG.warnf("Unknown state-backing value '%s' for NEGOTIATION — falling back to projection", backing);
        }

        return projectionEvaluator.evaluate(ctx);
    }
}

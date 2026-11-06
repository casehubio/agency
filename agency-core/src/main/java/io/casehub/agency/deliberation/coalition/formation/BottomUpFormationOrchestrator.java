package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.communication.channel.ChannelObserver;
import io.casehub.agency.core.AgentRef;
import io.casehub.agency.deliberation.coalition.CoalitionContext;
import io.casehub.agency.deliberation.coalition.CoalitionEvaluator;
import io.casehub.agency.deliberation.coalition.CoalitionProposal;
import io.casehub.agency.deliberation.negotiation.AcceptancePolicy;
import io.casehub.agency.deliberation.negotiation.NegotiationOutcome;
import io.casehub.agency.deliberation.negotiation.NegotiationProjection;
import io.casehub.agency.deliberation.negotiation.NegotiationState;
import io.casehub.eidos.org.api.GroupLifecycleService;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class BottomUpFormationOrchestrator {

    private final CoalitionEvaluator evaluator;
    private final GroupLifecycleService groupLifecycle;
    private final ChannelManager channelManager;
    private final MessageDispatcher messageDispatcher;
    private final MessageObserverRegistrar observerRegistrar;
    private final String specId;
    private final String tenancyId;
    private final AcceptancePolicy acceptancePolicy;
    private final Duration timeout;

    public BottomUpFormationOrchestrator(
            CoalitionEvaluator evaluator,
            GroupLifecycleService groupLifecycle,
            ChannelManager channelManager,
            MessageDispatcher messageDispatcher,
            MessageObserverRegistrar observerRegistrar,
            String specId,
            String tenancyId,
            AcceptancePolicy acceptancePolicy,
            Duration timeout) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.groupLifecycle = Objects.requireNonNull(groupLifecycle);
        this.channelManager = Objects.requireNonNull(channelManager);
        this.messageDispatcher = Objects.requireNonNull(messageDispatcher);
        this.observerRegistrar = Objects.requireNonNull(observerRegistrar);
        this.specId = Objects.requireNonNull(specId);
        this.tenancyId = Objects.requireNonNull(tenancyId);
        this.acceptancePolicy = Objects.requireNonNull(acceptancePolicy);
        this.timeout = Objects.requireNonNull(timeout);
    }

    public Uni<FormationResult> propose(CoalitionProposal proposal, CoalitionContext context) {
        var score = evaluator.evaluate(proposal, context);
        if (!score.isViable()) {
            return Uni.createFrom().item(FormationResult.rejected(score.reason()));
        }

        var memberNames = proposal.proposedMembers().stream()
                .map(AgentRef::name)
                .toList();

        var allPartyNames = new LinkedHashSet<>(memberNames);
        allPartyNames.add(proposal.proposer().name());

        var allParticipants = new ArrayList<>(memberNames);
        if (!allParticipants.contains(proposal.proposer().name())) {
            allParticipants.add(proposal.proposer().name());
        }

        var channelName = "formation/" + slugify(proposal.taskId()) + "/" + slugify(proposal.proposer().name());

        var findResult = channelManager.findOrCreate(
                ChannelCreateRequest.builder(channelName)
                        .protocols(List.of("NEGOTIATION"))
                        .protocolParticipants(allParticipants)
                        .allowedWriters(allParticipants)
                        .build());
        var channelId = findResult.channel().id();

        var projection = new NegotiationProjection(allPartyNames, acceptancePolicy);
        var observer = ChannelObserver.of(projection, channelName);
        var registrationCancel = observerRegistrar.register(observer);

        messageDispatcher.dispatch(MessageDispatch.builder()
                .channelId(channelId)
                .sender(proposal.proposer().name())
                .type(MessageType.PROPOSE)
                .actorType(ActorType.AGENT)
                .correlationId(proposal.taskId())
                .tenancyId(tenancyId)
                .content("Coalition proposal: " + proposal.taskId()
                        + " requires: " + proposal.requiredCapabilities())
                .build());

        return Uni.createFrom().<FormationResult>emitter(emitter -> {
            var subscription = observer.subscribe(event -> {
                var state = observer.currentState();
                if (state.outcome().isTerminal()) {
                    emitter.complete(handleOutcome(state, proposal));
                }
            });

            emitter.onTermination(() -> {
                subscription.cancel();
                registrationCancel.run();
                channelManager.pause(channelId);
            });
        }).ifNoItem().after(timeout).recoverWithUni(() -> {
            var state = observer.currentState();
            if (state.outcome().isTerminal()) {
                return Uni.createFrom().item(handleOutcome(state, proposal));
            }
            return Uni.createFrom().item(FormationResult.timeout());
        });
    }

    private static String slugify(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private FormationResult handleOutcome(NegotiationState state, CoalitionProposal proposal) {
        if (state.outcome() == NegotiationOutcome.AGREED) {
            var instance = groupLifecycle.proposeGroup(specId, tenancyId, proposal.proposer().name());
            groupLifecycle.approveProposal(instance.instanceId(), tenancyId);
            for (var member : proposal.proposedMembers()) {
                groupLifecycle.joinGroup(instance.instanceId(), member.name(), tenancyId);
            }
            return FormationResult.accepted(instance.instanceId());
        }
        return FormationResult.negotiationFailed(state.outcome().name());
    }
}

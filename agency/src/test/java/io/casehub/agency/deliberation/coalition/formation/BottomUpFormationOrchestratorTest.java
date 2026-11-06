package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.deliberation.coalition.CoalitionContext;
import io.casehub.agency.deliberation.coalition.CoalitionEvaluator;
import io.casehub.agency.deliberation.coalition.CoalitionProposal;
import io.casehub.agency.deliberation.coalition.CoalitionScore;
import io.casehub.agency.deliberation.negotiation.UnanimousAcceptance;
import io.casehub.eidos.org.api.GroupInstance;
import io.casehub.eidos.org.api.GroupLifecycleService;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BottomUpFormationOrchestratorTest {

    private CoalitionEvaluator evaluator;
    private GroupLifecycleService groupLifecycle;
    private ChannelManager channelManager;
    private MessageDispatcher messageDispatcher;
    private MessageObserverRegistrar observerRegistrar;

    private static final String SPEC_ID = "test-spec";
    private static final String TENANCY_ID = "tenant-1";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String CHANNEL_NAME = "formation/task-1/proposer-1";

    @BeforeEach
    void setUp() {
        evaluator = mock(CoalitionEvaluator.class);
        groupLifecycle = mock(GroupLifecycleService.class);
        channelManager = mock(ChannelManager.class);
        messageDispatcher = mock(MessageDispatcher.class);
        observerRegistrar = mock(MessageObserverRegistrar.class);
    }

    private BottomUpFormationOrchestrator orchestrator() {
        return new BottomUpFormationOrchestrator(
                evaluator, groupLifecycle, channelManager,
                messageDispatcher, observerRegistrar,
                SPEC_ID, TENANCY_ID, new UnanimousAcceptance(), TIMEOUT);
    }

    private static AgentRef agent(String name) {
        return new AgentRef.ExternalAgent(name, null);
    }

    private CoalitionProposal proposal() {
        return new CoalitionProposal(
                agent("proposer-1"), "task-1",
                Set.of("analysis", "reporting"),
                List.of(agent("agent-a"), agent("agent-b")));
    }

    private CoalitionContext context() {
        return new CoalitionContext(
                List.of(agent("agent-a"), agent("agent-b")),
                Map.of("agent-a", Set.of("analysis"),
                       "agent-b", Set.of("reporting")));
    }

    private void stubViableCoalition() {
        when(evaluator.evaluate(any(), any()))
                .thenReturn(new CoalitionScore(1.0, 1.0, Set.of(), "full coverage"));
    }

    private UUID stubChannelCreation() {
        var channelId = UUID.randomUUID();
        var channel = mock(Channel.class);
        when(channel.id()).thenReturn(channelId);
        when(channel.name()).thenReturn(CHANNEL_NAME);
        when(channelManager.findOrCreate(any())).thenReturn(new FindOrCreateResult(channel, true));
        when(channelManager.pause(any())).thenReturn(channel);
        return channelId;
    }

    private void stubGroupCreation(String instanceId) {
        var instance = mock(GroupInstance.class);
        when(instance.instanceId()).thenReturn(instanceId);
        when(groupLifecycle.proposeGroup(any(), any(), any())).thenReturn(instance);
    }

    private AtomicReference<MessageObserver> captureObserver() {
        var ref = new AtomicReference<MessageObserver>();
        when(observerRegistrar.register(any())).thenAnswer(inv -> {
            ref.set(inv.getArgument(0));
            return (Runnable) () -> {};
        });
        return ref;
    }

    private MessageReceivedEvent event(UUID channelId, String sender,
                                        MessageType type, String correlationId) {
        return new MessageReceivedEvent(
                1L, CHANNEL_NAME, channelId, TENANCY_ID,
                type, sender, null, ActorType.AGENT,
                correlationId, Instant.now(), "content", null, null);
    }

    // --- Tests ---

    @Test
    void nonViableCoalition_returnsRejected() {
        when(evaluator.evaluate(any(), any()))
                .thenReturn(new CoalitionScore(0.4, 0.5, Set.of("missing"), "insufficient"));

        var result = orchestrator().propose(proposal(), context())
                .await().atMost(Duration.ofSeconds(2));

        assertThat(result).isInstanceOf(FormationResult.Rejected.class);
        assertThat(((FormationResult.Rejected) result).reason()).isEqualTo("insufficient");
        verifyNoInteractions(channelManager, messageDispatcher, groupLifecycle, observerRegistrar);
    }

    @Test
    void agreedNegotiation_createsGroupWithMembers() {
        stubViableCoalition();
        var channelId = stubChannelCreation();
        stubGroupCreation("group-1");
        var observerRef = captureObserver();

        var resultRef = new AtomicReference<FormationResult>();
        orchestrator().propose(proposal(), context())
                .subscribe().with(resultRef::set);

        var observer = observerRef.get();
        assertThat(observer).isNotNull();

        // Proposer sends PROPOSE (orchestrator already dispatched, but observer also sees it)
        observer.onMessage(event(channelId, "proposer-1", MessageType.PROPOSE, "task-1"));
        // Agent-a accepts
        observer.onMessage(event(channelId, "agent-a", MessageType.DONE, "task-1"));
        // Agent-b accepts
        observer.onMessage(event(channelId, "agent-b", MessageType.DONE, "task-1"));

        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get()).isInstanceOf(FormationResult.Accepted.class);
        assertThat(((FormationResult.Accepted) resultRef.get()).groupInstanceId()).isEqualTo("group-1");

        verify(groupLifecycle).proposeGroup(SPEC_ID, TENANCY_ID, "proposer-1");
        verify(groupLifecycle).approveProposal("group-1", TENANCY_ID);
        verify(groupLifecycle).joinGroup("group-1", "agent-a", TENANCY_ID);
        verify(groupLifecycle).joinGroup("group-1", "agent-b", TENANCY_ID);
        verify(channelManager).pause(channelId);
    }

    @Test
    void withdrawnNegotiation_returnsNegotiationFailed() {
        stubViableCoalition();
        var channelId = stubChannelCreation();
        var observerRef = captureObserver();

        var resultRef = new AtomicReference<FormationResult>();
        orchestrator().propose(proposal(), context())
                .subscribe().with(resultRef::set);

        var observer = observerRef.get();
        observer.onMessage(event(channelId, "proposer-1", MessageType.PROPOSE, "task-1"));
        // Proposer withdraws
        observer.onMessage(event(channelId, "proposer-1", MessageType.DECLINE, "task-1"));

        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get()).isInstanceOf(FormationResult.NegotiationFailed.class);
        assertThat(((FormationResult.NegotiationFailed) resultRef.get()).reason()).isEqualTo("WITHDRAWN");
        verifyNoInteractions(groupLifecycle);
        verify(channelManager).pause(channelId);
    }

    @Test
    void timeout_returnsTimeout() {
        stubViableCoalition();
        stubChannelCreation();
        captureObserver();

        var shortOrchestrator = new BottomUpFormationOrchestrator(
                evaluator, groupLifecycle, channelManager,
                messageDispatcher, observerRegistrar,
                SPEC_ID, TENANCY_ID, new UnanimousAcceptance(),
                Duration.ofMillis(100));

        var result = shortOrchestrator.propose(proposal(), context())
                .await().atMost(Duration.ofSeconds(2));

        assertThat(result).isInstanceOf(FormationResult.Timeout.class);
        verifyNoInteractions(groupLifecycle);
    }

    @Test
    void proposeMessage_hasCorrectFields() {
        stubViableCoalition();
        stubChannelCreation();
        captureObserver();

        orchestrator().propose(proposal(), context());

        var captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());

        var dispatch = captor.getValue();
        assertThat(dispatch.sender()).isEqualTo("proposer-1");
        assertThat(dispatch.type()).isEqualTo(MessageType.PROPOSE);
        assertThat(dispatch.correlationId()).isEqualTo("task-1");
        assertThat(dispatch.actorType()).isEqualTo(ActorType.AGENT);
        assertThat(dispatch.tenancyId()).isEqualTo(TENANCY_ID);
        assertThat(dispatch.content()).isNotBlank();
    }

    @Test
    void channelName_isDeterministic() {
        stubViableCoalition();
        stubChannelCreation();
        captureObserver();

        orchestrator().propose(proposal(), context());

        var captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelManager).findOrCreate(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("formation/task-1/proposer-1");
    }

    @Test
    void channelRequest_includesProposerInParticipants() {
        stubViableCoalition();
        stubChannelCreation();
        captureObserver();

        orchestrator().propose(proposal(), context());

        var captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelManager).findOrCreate(captor.capture());
        assertThat(captor.getValue().protocolParticipants())
                .contains("proposer-1", "agent-a", "agent-b");
    }
}

package io.casehub.agency.communication.channel;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.FailurePolicy;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.activation.OnExplicitDispatch;
import io.casehub.agency.orchestration.aggregation.PassThrough;
import io.casehub.agency.orchestration.decomposition.IdentityDecomposition;
import io.casehub.agency.orchestration.execution.AgentInvoker;
import io.casehub.agency.orchestration.execution.ExecutionModel;
import io.casehub.agency.orchestration.execution.ExecutionResult;
import io.casehub.agency.orchestration.execution.PatternType;
import io.casehub.agency.orchestration.routing.FirstMatchRouting;
import io.casehub.agency.orchestration.termination.MaxIterationsTermination;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.agency.deliberation.conversation.orchestration.AgentParticipant;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationChannelAdapterTest {

    @Test
    void mapsModelCandidatesToParticipants() {
        var agent1 = AgentRef.external("analyst", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "analysis")));
        var agent2 = AgentRef.external("critic", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "critique")));

        var invocations = new AtomicInteger();
        AgentInvoker<String> countingInvoker = (agent, state) -> {
            invocations.incrementAndGet();
            return io.smallrye.mutiny.Uni.createFrom().item(
                    AgentResult.success(agent, "response-" + invocations.get()));
        };

        var config = new ConversationConfig<String>(
                (agent, drain, state) -> "prompt for " + agent.agentId(),
                (agent, result, state) -> mockMessage(agent.agentId(), result.output().toString()),
                s -> mockMessage("system", s),
                null, countingInvoker, null, null, null);

        @SuppressWarnings("unchecked")
        var termination = (TerminationCondition<ConversationState>)
                (TerminationCondition<?>) new MaxIterationsTermination<>(1);

        var strategy = new ChannelExecutionStrategy.Conversation<>(
                config, termination,
                agent -> new AgentParticipant(agent, agent.name(), null));

        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.APPEND);
        var model = buildModel(PatternType.DEBATE, agent1, agent2);
        var dispatcher = mock(MessageDispatcher.class);

        var result = strategy.run(binding, model, "debate topic", dispatcher)
                .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void mapsCompletedOutcomeToCompletedResult() {
        var agent = AgentRef.external("solo", (Object ctx) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "done")));

        AgentInvoker<String> invoker = (a, s) ->
                io.smallrye.mutiny.Uni.createFrom().item(AgentResult.success(a, "ok"));

        var config = new ConversationConfig<String>(
                (ag, drain, state) -> "prompt",
                (ag, result, state) -> mockMessage(ag.agentId(), "reply"),
                s -> mockMessage("trigger", s),
                null, invoker, null, null, null);

        @SuppressWarnings("unchecked")
        var termination = (TerminationCondition<ConversationState>)
                (TerminationCondition<?>) new MaxIterationsTermination<>(1);

        var strategy = new ChannelExecutionStrategy.Conversation<>(
                config, termination,
                a -> new AgentParticipant(a, "role", null));

        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.APPEND);
        var model = buildModel(null, agent);
        var dispatcher = mock(MessageDispatcher.class);

        var result = strategy.run(binding, model, "go", dispatcher)
                .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        var completed = (ExecutionResult.Completed) result;
        assertThat(completed.result()).isInstanceOf(ConversationState.class);
    }

    private static MessageView mockMessage(String sender, String content) {
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn(sender);
        when(msg.content()).thenReturn(content);
        when(msg.correlationId()).thenReturn("corr-" + System.nanoTime());
        when(msg.id()).thenReturn(System.nanoTime());
        when(msg.type()).thenReturn(null);
        when(msg.createdAt()).thenReturn(Instant.now());
        when(msg.topic()).thenReturn("general");
        return msg;
    }

    private static ExecutionModel<String> buildModel(PatternType patternType, AgentRef... agents) {
        var candidates = java.util.Arrays.stream(agents)
                .map(a -> new RoutingCandidate(a, null)).toList();
        return new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), () -> candidates,
                FailurePolicy.defaults(), List.of(), "test", patternType);
    }
}

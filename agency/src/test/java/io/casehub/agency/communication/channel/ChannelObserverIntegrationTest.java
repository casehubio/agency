package io.casehub.agency.communication.channel;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.FailurePolicy;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.activation.OnExplicitDispatch;
import io.casehub.agency.orchestration.aggregation.PassThrough;
import io.casehub.agency.orchestration.decomposition.IdentityDecomposition;
import io.casehub.agency.orchestration.execution.AgentInvoker;
import io.casehub.agency.orchestration.execution.ChoreographedDriver;
import io.casehub.agency.orchestration.execution.EventConcurrencyPolicy;
import io.casehub.agency.orchestration.execution.ExecutionModel;
import io.casehub.agency.orchestration.execution.ExecutionResult;
import io.casehub.agency.orchestration.routing.FirstMatchRouting;
import io.casehub.agency.orchestration.termination.MaxIterationsTermination;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelObserverIntegrationTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private static final ChannelProjection<List<String>> COLLECTING =
        new ChannelProjection<>() {
            @Override public List<String> identity() { return List.of(); }
            @Override public List<String> apply(List<String> state, MessageView msg) {
                var next = new ArrayList<>(state);
                next.add(msg.content());
                return List.copyOf(next);
            }
        };

    private static MessageReceivedEvent event(String content) {
        return new MessageReceivedEvent(
            1L, "test-channel", CHANNEL_ID, "tenant",
            MessageType.STATUS, "agent-1", null, null,
            null, Instant.now(), content, null, null);
    }

    @Test
    void observerAsEventSourceWakesDriverAndTerminatesOnProjectionState() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");

        var callCount = new AtomicInteger(0);
        var agent = AgentRef.external((Object input) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(
                AgentResult.success(null, "done"));
        });

        var termination = observer.<String>terminateWhen(
                state -> state.contains("stop"))
            .or(new MaxIterationsTermination<>(10));

        var model = new ExecutionModel<>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            new OnExplicitDispatch<>(),
            new PassThrough<>(),
            termination,
            () -> List.of(new RoutingCandidate(agent, null)),
            FailurePolicy.defaults(),
            List.of(), "test");

        var driver = new ChoreographedDriver<>(
            AgentInvoker.<String>defaultInvoker(),
            EventConcurrencyPolicy.serialize(),
            observer);

        new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            observer.onMessage(event("hello"));
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            observer.onMessage(event("stop"));
        }).start();

        var result = driver.execute(model, "initial").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(observer.currentState()).containsExactly("hello", "stop");
        assertThat(callCount.get()).isGreaterThanOrEqualTo(1);
    }
}

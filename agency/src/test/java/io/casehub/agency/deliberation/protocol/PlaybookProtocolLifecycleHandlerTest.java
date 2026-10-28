package io.casehub.agency.deliberation.protocol;

import io.casehub.platform.coordination.protocol.PlaybookProtocolEvaluator;
import io.casehub.platform.coordination.protocol.ProtocolInstanceKey;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlaybookProtocolLifecycleHandlerTest {

    @SuppressWarnings("unchecked")
    private Instance<PlaybookProtocolEvaluator> satisfiedInstance(PlaybookProtocolEvaluator evaluator) {
        Instance<PlaybookProtocolEvaluator> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.get()).thenReturn(evaluator);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private Instance<PlaybookProtocolEvaluator> unsatisfiedInstance() {
        Instance<PlaybookProtocolEvaluator> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(true);
        return instance;
    }

    @Test
    void supports_negotiation_when_evaluator_available() {
        var handler = new PlaybookProtocolLifecycleHandler(
                satisfiedInstance(mock(PlaybookProtocolEvaluator.class)));
        assertThat(handler.supports("NEGOTIATION")).isTrue();
    }

    @Test
    void does_not_support_negotiation_when_evaluator_unavailable() {
        var handler = new PlaybookProtocolLifecycleHandler(unsatisfiedInstance());
        assertThat(handler.supports("NEGOTIATION")).isFalse();
    }

    @Test
    void does_not_support_unknown_protocol() {
        var handler = new PlaybookProtocolLifecycleHandler(
                satisfiedInstance(mock(PlaybookProtocolEvaluator.class)));
        assertThat(handler.supports("UNKNOWN")).isFalse();
    }

    @Test
    void onInit_registers_template() {
        PlaybookProtocolEvaluator evaluator = mock(PlaybookProtocolEvaluator.class);
        var handler = new PlaybookProtocolLifecycleHandler(satisfiedInstance(evaluator));

        var context = new ProtocolLifecycleContext(
                UUID.randomUUID(), "test", "NEGOTIATION",
                List.of("alice", "bob"), List.of(), false);

        handler.onInit(context);

        verify(evaluator).register(eq("NEGOTIATION"), any(Map.class));
    }

    @Test
    void onInit_replays_history_on_recovery() {
        PlaybookProtocolEvaluator evaluator = mock(PlaybookProtocolEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(List.of());
        var handler = new PlaybookProtocolLifecycleHandler(satisfiedInstance(evaluator));

        var msg1 = mock(MessageView.class);
        when(msg1.type()).thenReturn(MessageType.PROPOSE);
        when(msg1.sender()).thenReturn("alice");
        when(msg1.correlationId()).thenReturn("c1");

        var msg2 = mock(MessageView.class);
        when(msg2.type()).thenReturn(MessageType.DONE);
        when(msg2.sender()).thenReturn("bob");
        when(msg2.correlationId()).thenReturn("c2");

        UUID channelId = UUID.randomUUID();
        var context = new ProtocolLifecycleContext(
                channelId, "test", "NEGOTIATION",
                List.of("alice", "bob"), List.of(msg1, msg2), true);

        handler.onInit(context);

        verify(evaluator).register(eq("NEGOTIATION"), any(Map.class));
        verify(evaluator).evaluate(
                eq(new ProtocolInstanceKey("NEGOTIATION", channelId.toString())),
                eq("PROPOSE"), any(Map.class));
        verify(evaluator).evaluate(
                eq(new ProtocolInstanceKey("NEGOTIATION", channelId.toString())),
                eq("DONE"), any(Map.class));
    }

    @Test
    void onInit_does_not_replay_when_not_recovered() {
        PlaybookProtocolEvaluator evaluator = mock(PlaybookProtocolEvaluator.class);
        var handler = new PlaybookProtocolLifecycleHandler(satisfiedInstance(evaluator));

        var context = new ProtocolLifecycleContext(
                UUID.randomUUID(), "test", "NEGOTIATION",
                List.of("alice", "bob"), List.of(), false);

        handler.onInit(context);

        verify(evaluator).register(eq("NEGOTIATION"), any(Map.class));
        verify(evaluator, never()).evaluate(any(), any(), any());
    }

    @Test
    void onDestroy_evicts_instance() {
        PlaybookProtocolEvaluator evaluator = mock(PlaybookProtocolEvaluator.class);
        var handler = new PlaybookProtocolLifecycleHandler(satisfiedInstance(evaluator));

        UUID channelId = UUID.randomUUID();
        handler.onDestroy(channelId, "NEGOTIATION");

        verify(evaluator).evict(
                eq(new ProtocolInstanceKey("NEGOTIATION", channelId.toString())));
    }
}

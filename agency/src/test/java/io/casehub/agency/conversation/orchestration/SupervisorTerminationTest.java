package io.casehub.agency.conversation.orchestration;

import io.casehub.agency.agentic.termination.TerminationContext;
import io.casehub.agency.agentic.termination.TerminationDecision;
import io.casehub.agency.conversation.ConversationFold;
import io.casehub.agency.conversation.ConversationState;
import io.casehub.agency.conversation.PointClassification;
import io.casehub.agency.conversation.Priority;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupervisorTerminationTest {

    private final SupervisorTermination termination =
            new SupervisorTermination("SUPERVISOR", "TERMINATE");

    @Test
    void noSupervisorSignal_continues() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "topic",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
        assertThat(evaluate(state)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void supervisorSignal_completes() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "topic",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
        state = ConversationFold.respondToPoint(state, "p1",
                2L, null, "supervisor", Instant.now(),
                "SUPERVISOR", 1, "TERMINATE", "Discussion complete", null);
        var decision = evaluate(state);
        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
        assertThat(((TerminationDecision.Complete) decision).result())
                .isEqualTo("Discussion complete");
    }

    @Test
    void wrongRole_continues() {
        var state = new ConversationState(Map.of(), List.of(), List.of(), Map.of());
        state = ConversationFold.createPoint(state, "p1", "topic",
                1L, null, "alice", Instant.now(),
                new PointClassification(Priority.MEDIUM, null, null), "REV", 1, "RAISE", "content");
        state = ConversationFold.respondToPoint(state, "p1",
                2L, null, "bob", Instant.now(),
                "IMP", 1, "TERMINATE", "I say stop", null);
        assertThat(evaluate(state)).isInstanceOf(TerminationDecision.Continue.class);
    }

    private TerminationDecision evaluate(ConversationState state) {
        var ctx = new TerminationContext<>(state, 1, Duration.ZERO, List.of());
        return termination.evaluate(ctx);
    }
}

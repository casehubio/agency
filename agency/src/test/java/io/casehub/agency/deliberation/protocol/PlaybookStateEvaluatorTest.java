package io.casehub.agency.deliberation.protocol;

import io.casehub.platform.coordination.protocol.AdvisorySeverity;
import io.casehub.platform.coordination.protocol.PlaybookProtocolEvaluator;
import io.casehub.platform.coordination.protocol.ProtocolAdvisory;
import io.casehub.platform.coordination.protocol.ProtocolInstanceKey;
import io.casehub.platform.coordination.protocol.SuggestedAction;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlaybookStateEvaluatorTest {

    private final PlaybookProtocolEvaluator playbookEvaluator = mock(PlaybookProtocolEvaluator.class);
    private final UUID channelId = UUID.randomUUID();

    private ProtocolContext ctx(MessageType type, String sender) {
        return new ProtocolContext(channelId, "test-channel", type, sender,
                "corr-1", List.of("alice", "bob"), List.of(), List.of(), Map.of());
    }

    @Test
    void evaluate_delegatesToPlaybookEvaluator() {
        when(playbookEvaluator.evaluate(any(), eq("PROPOSE"), any()))
                .thenReturn(List.of());

        var evaluator = new PlaybookStateEvaluator(playbookEvaluator, "NEGOTIATION");
        evaluator.evaluate(ctx(MessageType.PROPOSE, "alice"));

        verify(playbookEvaluator).evaluate(
                eq(new ProtocolInstanceKey("NEGOTIATION", channelId.toString())),
                eq("PROPOSE"),
                any(Map.class));
    }

    @Test
    void evaluate_mapsAdvisories() {
        var advisory = new ProtocolAdvisory(AdvisorySeverity.WARNING, "test warning",
                SuggestedAction.INVESTIGATE);
        when(playbookEvaluator.evaluate(any(), any(), any()))
                .thenReturn(List.of(advisory));

        var evaluator = new PlaybookStateEvaluator(playbookEvaluator, "NEGOTIATION");
        var result = evaluator.evaluate(ctx(MessageType.PROPOSE, "alice"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().severity()).isEqualTo(Severity.WARNING);
        assertThat(result.getFirst().message()).isEqualTo("test warning");
        assertThat(result.getFirst().source()).isEqualTo("NEGOTIATION");
    }

    @Test
    void evaluate_emptyAdvisories_returnsEmpty() {
        when(playbookEvaluator.evaluate(any(), any(), any()))
                .thenReturn(List.of());

        var evaluator = new PlaybookStateEvaluator(playbookEvaluator, "NEGOTIATION");
        var result = evaluator.evaluate(ctx(MessageType.DONE, "bob"));

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void evaluate_passesContextFields() {
        when(playbookEvaluator.evaluate(any(), any(), any()))
                .thenReturn(List.of());

        var evaluator = new PlaybookStateEvaluator(playbookEvaluator, "NEGOTIATION");
        evaluator.evaluate(ctx(MessageType.PROPOSE, "alice"));

        verify(playbookEvaluator).evaluate(any(), any(), argThat(ctx -> {
            Map<String, Object> map = (Map<String, Object>) ctx;
            return "alice".equals(map.get("sender"))
                && "corr-1".equals(map.get("correlationId"));
        }));
    }
}

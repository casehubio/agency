package io.casehub.agency.stigmergy.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.casehub.api.spi.observation.Observation;
import io.casehub.api.spi.observation.RuleAction;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoundationTypesTest {

    @Test
    void llmObservation_constructsWithAllFields() {
        var obs = new LlmObservation("risk-spike", 0.92,
            List.of("risk.score"), List.of(),
            Map.of("delta", 0.37), "Risk jumped");
        assertThat(obs.patternId()).isEqualTo("risk-spike");
        assertThat(obs.confidence()).isEqualTo(0.92);
        assertThat(obs.evidence()).containsExactly("risk.score");
        assertThat(obs.derivedFrom()).isEmpty();
        assertThat(obs.details()).containsEntry("delta", 0.37);
        assertThat(obs.reasoning()).isEqualTo("Risk jumped");
    }

    @Test
    void llmObservation_derivedFromReferencesParent() {
        var derived = new LlmObservation("compound", 0.88,
            List.of("tx.amount"), List.of("risk-spike"),
            Map.of(), "Compound pattern");
        assertThat(derived.derivedFrom()).containsExactly("risk-spike");
    }

    @Test
    void llmObservation_rejectsBlankPatternId() {
        assertThatThrownBy(() -> new LlmObservation("", 0.5,
            List.of(), List.of(), Map.of(), "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void llmObservation_rejectsConfidenceOutOfRange() {
        assertThatThrownBy(() -> new LlmObservation("p", 1.5,
            List.of(), List.of(), Map.of(), "x"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmObservation("p", -0.1,
            List.of(), List.of(), Map.of(), "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void llmObservation_defensiveCopiesOnLists() {
        var evidence = new java.util.ArrayList<>(List.of("a"));
        var obs = new LlmObservation("p", 0.5, evidence, List.of(), Map.of(), "r");
        evidence.add("b");
        assertThat(obs.evidence()).containsExactly("a");
    }

    @Test
    void llmObservationResponse_wrapsObservations() {
        var obs = new LlmObservation("p1", 0.9,
            List.of(), List.of(), Map.of(), "r");
        var response = new LlmObservationResponse(List.of(obs));
        assertThat(response.observations()).hasSize(1);
        assertThat(response.observations().get(0).patternId()).isEqualTo("p1");
    }

    @Test
    void llmRecommendation_constructsWithAllFields() {
        var rec = new LlmRecommendation("DepositSignal",
            Map.of("name", "alert"), List.of("risk-spike"),
            0.85, "alert needed");
        assertThat(rec.actionType()).isEqualTo("DepositSignal");
        assertThat(rec.justifyingPatternIds()).containsExactly("risk-spike");
        assertThat(rec.confidence()).isEqualTo(0.85);
    }

    @Test
    void llmActionResponse_wrapsRecommendations() {
        var rec = new LlmRecommendation("DepositSignal",
            Map.of("name", "alert"), List.of(), 0.8, "r");
        var response = new LlmActionResponse(List.of(rec));
        assertThat(response.actions()).hasSize(1);
    }

    @Test
    void llmInvocationPolicy_factoryMethod() {
        var policy = LlmInvocationPolicy.of(
            Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(5));
        assertThat(policy.expectedDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.warnAfter()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.errorAfter()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void llmInvocationPolicy_rejectsNulls() {
        assertThatThrownBy(() -> LlmInvocationPolicy.of(null, Duration.ZERO, Duration.ZERO))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recommendedAction_wrapsRuleActionWithAttribution() {
        var action = new RecommendedAction(
            new RuleAction.DepositSignal("alert", 0.9, null),
            List.of("risk-spike"), 0.85);
        assertThat(action.justifyingPatternIds()).containsExactly("risk-spike");
        assertThat(action.action()).isInstanceOf(RuleAction.DepositSignal.class);
        assertThat(action.confidence()).isEqualTo(0.85);
    }

    @Test
    void recommendedAction_rejectsNullAction() {
        assertThatThrownBy(() -> new RecommendedAction(null, List.of(), 0.5))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recommendedAction_rejectsConfidenceOutOfRange() {
        assertThatThrownBy(() -> new RecommendedAction(
            new RuleAction.DepositSignal("a", 0.5, null), List.of(), 1.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void llmObservationResult_carriesParallelRawAndConverted() {
        var raw = new LlmObservation("p1", 0.9, List.of(), List.of(), Map.of(), "r");
        var engine = new Observation("p1", 0.9, Map.of(), Instant.now());
        var result = new LlmObservationResult(
            List.of(engine), List.of(raw), 0.9, "reasoning",
            Instant.now(), Instant.now(), 5,
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
        assertThat(result.observations()).hasSize(1);
        assertThat(result.rawObservations()).hasSize(1);
        assertThat(result.rawObservations().get(0).patternId())
            .isEqualTo(result.observations().get(0).patternId());
        assertThat(result.batchEventCount()).isEqualTo(5);
    }

    @Test
    void actionResult_carriesRecommendations() {
        var action = new RecommendedAction(
            new RuleAction.DepositSignal("a", 0.5, null),
            List.of("p1"), 0.8);
        var result = new ActionResult(
            List.of(action), 0.85, "reasoning",
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
        assertThat(result.recommendedActions()).hasSize(1);
        assertThat(result.confidence()).isEqualTo(0.85);
    }

    @Test
    void interpretationResult_combinesObservationsAndActions() {
        var engine = new Observation("p1", 0.9, Map.of(), Instant.now());
        var action = new RecommendedAction(
            new RuleAction.DepositSignal("a", 0.5, null),
            List.of("p1"), 0.8);
        var result = new InterpretationResult(
            List.of(engine), List.of(action), 0.87, "reasoning",
            Instant.now(), Instant.now(), 3,
            StructuredAgentInvoker.InvocationMetadata.EMPTY);
        assertThat(result.observations()).hasSize(1);
        assertThat(result.recommendedActions()).hasSize(1);
    }

    @Test
    void environmentWriter_isAnInterface() {
        assertThat(EnvironmentWriter.class.isInterface()).isTrue();
    }

    @Test
    void observationHandler_isFunctionalInterface() {
        assertThat(ObservationHandler.class.isAnnotation()).isFalse();
        ObservationHandler handler = (obs, raw, ctx) -> {};
        assertThat(handler).isNotNull();
    }
}

package io.casehub.agency.stigmergy.llm;

import io.casehub.api.spi.observation.RuleAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseConverterTest {

    @Test
    void toObservations_convertsPatternIdAndConfidence() {
        var raw = new LlmObservation("risk-spike", 0.92,
            List.of("risk.score"), List.of(), Map.of("delta", 0.37), "jumped");
        var result = LlmResponseConverter.toObservations(
            new LlmObservationResponse(List.of(raw)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).patternId()).isEqualTo("risk-spike");
        assertThat(result.get(0).confidence()).isEqualTo(0.92);
    }

    @Test
    void toObservations_packsEvidenceAndDerivedFromIntoDetails() {
        var raw = new LlmObservation("compound", 0.88,
            List.of("tx.amount"), List.of("risk-spike"),
            Map.of("threshold", "regulatory"), "compound pattern");
        var result = LlmResponseConverter.toObservations(
            new LlmObservationResponse(List.of(raw)));
        var details = result.get(0).details();
        assertThat(details).containsKey("evidence");
        assertThat(details).containsKey("derivedFrom");
        assertThat(details).containsKey("reasoning");
    }

    @Test
    void toObservations_preservesOriginalDetails() {
        var raw = new LlmObservation("p1", 0.9,
            List.of(), List.of(), Map.of("custom", "value"), "r");
        var result = LlmResponseConverter.toObservations(
            new LlmObservationResponse(List.of(raw)));
        var details = result.get(0).details();
        assertThat(details).containsKey("custom");
    }

    @Test
    void toObservations_multipleObservations() {
        var raw1 = new LlmObservation("p1", 0.9,
            List.of(), List.of(), Map.of(), "r1");
        var raw2 = new LlmObservation("p2", 0.8,
            List.of(), List.of(), Map.of(), "r2");
        var result = LlmResponseConverter.toObservations(
            new LlmObservationResponse(List.of(raw1, raw2)));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).patternId()).isEqualTo("p1");
        assertThat(result.get(1).patternId()).isEqualTo("p2");
    }

    @Test
    void toRecommendedActions_convertsDepositSignal() {
        var rec = new LlmRecommendation("DepositSignal",
            Map.of("name", "alert", "strength", 0.9),
            List.of("risk-spike"), 0.85, "alert needed");
        var result = LlmResponseConverter.toRecommendedActions(
            new LlmActionResponse(List.of(rec)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isInstanceOf(RuleAction.DepositSignal.class);
        var ds = (RuleAction.DepositSignal) result.get(0).action();
        assertThat(ds.name()).isEqualTo("alert");
        assertThat(ds.strength()).isEqualTo(0.9);
    }

    @Test
    void toRecommendedActions_convertsWriteContext() {
        var rec = new LlmRecommendation("WriteContext",
            Map.of("key", "status", "value", "critical"),
            List.of("p1"), 0.9, "update status");
        var result = LlmResponseConverter.toRecommendedActions(
            new LlmActionResponse(List.of(rec)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isInstanceOf(RuleAction.WriteContext.class);
        var wc = (RuleAction.WriteContext) result.get(0).action();
        assertThat(wc.key()).isEqualTo("status");
    }

    @Test
    void toRecommendedActions_dropsUnknownActionType() {
        var rec = new LlmRecommendation("FlyToMoon",
            Map.of(), List.of(), 0.5, "invalid");
        var result = LlmResponseConverter.toRecommendedActions(
            new LlmActionResponse(List.of(rec)));
        assertThat(result).isEmpty();
    }

    @Test
    void toRecommendedActions_dropsMissingRequiredParams() {
        var rec = new LlmRecommendation("DepositSignal",
            Map.of(), List.of(), 0.5, "missing name");
        var result = LlmResponseConverter.toRecommendedActions(
            new LlmActionResponse(List.of(rec)));
        assertThat(result).isEmpty();
    }

    @Test
    void toRecommendedActions_preservesJustifyingPatternIds() {
        var rec = new LlmRecommendation("DepositSignal",
            Map.of("name", "alert", "strength", 0.9),
            List.of("p1", "p2"), 0.85, "r");
        var result = LlmResponseConverter.toRecommendedActions(
            new LlmActionResponse(List.of(rec)));
        assertThat(result.get(0).justifyingPatternIds())
            .containsExactly("p1", "p2");
        assertThat(result.get(0).confidence()).isEqualTo(0.85);
    }
}

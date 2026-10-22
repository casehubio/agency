package io.casehub.engine.agentic.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import io.casehub.api.spi.judgment.CallerConfig;
import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.deliberation.judgment.JudgmentContext;
import io.casehub.agency.deliberation.judgment.JudgmentDecision;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmJudgmentPhaseTest {

  private AgentProvider provider;

  @BeforeEach
  void setUp() {
    provider = mock(AgentProvider.class);
  }

  @Test
  void approved_whenLlmReturnsApproveResponse() {
    mockLlmResponse("APPROVE: The analysis is thorough and well-structured.");
    var config = new PatternJudgmentConfig(
        "Review the output",
        new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
  }

  @Test
  void rejected_whenLlmReturnsRejectResponse() {
    mockLlmResponse("REJECT: Missing error handling analysis.");
    var config = new PatternJudgmentConfig(
        "Review the output",
        new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Rejected.class);
    assertThat(((JudgmentDecision.Rejected) decision).feedback())
        .contains("Missing error handling");
  }

  @Test
  void previousFeedback_includedInPrompt() {
    mockLlmResponse("APPROVE: Now looks complete.");
    var config = new PatternJudgmentConfig(
        "Review the output",
        new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 1,
        "needs more detail");
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
    var captured = org.mockito.ArgumentCaptor.forClass(AgentSessionConfig.class);
    verify(provider).invoke(captured.capture());
    assertThat(captured.getValue().userPrompt()).contains("needs more detail");
  }

  @Test
  void verifierRejects_returnsRejected() {
    mockLlmResponse("APPROVE: Looks good.");
    var verifier = mock(JudgmentVerifier.class);
    when(verifier.verify(org.mockito.ArgumentMatchers.any(VerificationContext.class)))
        .thenReturn(new VerificationResult.Rejected("schema mismatch"));

    var config = new PatternJudgmentConfig(
        "Review",
        new CallerConfig.Llm(null, "test-model", null),
        "schema-validation", List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, verifier);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Rejected.class);
    assertThat(((JudgmentDecision.Rejected) decision).feedback()).contains("schema mismatch");
  }

  @Test
  void chatModelFailure_returnsEscalated() {
    when(provider.invoke(any())).thenReturn(Multi.createFrom().failure(new RuntimeException("API error")));
    var config = new PatternJudgmentConfig(
        "Review",
        new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Escalated.class);
  }

  @Test
  void errorCompletion_returnsEscalated() {
    var completion = new AgentEvent.InvocationComplete(
        0, 0, 0, 0, 0, null, 0, 0, null, 0, true, null, null);
    when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
        new AgentEvent.TextDelta("APPROVE"), completion));
    var config = new PatternJudgmentConfig(
        "Review", new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);
    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);

    assertThat(phase.evaluate(ctx)).isInstanceOf(JudgmentDecision.Escalated.class);
  }

  private void mockLlmResponse(String text) {
    int midpoint = text.length() / 2;
    when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
        new AgentEvent.TextDelta(text.substring(0, midpoint)),
        new AgentEvent.TextDelta(text.substring(midpoint))));
  }
}

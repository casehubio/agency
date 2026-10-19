/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.agentic.judgment;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.api.spi.judgment.Evidence;
import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import java.util.Optional;

public class LlmEvaluationVerifier implements JudgmentVerifier {

  private static final System.Logger LOG = System.getLogger(LlmEvaluationVerifier.class.getName());

  private final Optional<AgentProvider> agentProvider;

  public LlmEvaluationVerifier(Optional<AgentProvider> agentProvider) {
    this.agentProvider = agentProvider;
  }

  @Override
  public String id() {
    return "llm-evaluation";
  }

  @Override
  public VerificationResult verify(VerificationContext context) {
    if (agentProvider.isEmpty()) {
      LOG.log(System.Logger.Level.WARNING,
          "No AgentProvider on classpath — accepting response without LLM evaluation");
      return new VerificationResult.Accepted();
    }

    try {
      return evaluate(context);
    } catch (Exception e) {
      LOG.log(System.Logger.Level.WARNING, "LLM evaluation failed — accepting response as fallback", e);
      return new VerificationResult.Accepted();
    }
  }

  private VerificationResult evaluate(VerificationContext context) {
    String evaluationPrompt = buildEvaluationPrompt(context);
    String evaluation = AgentText.invoke(
        agentProvider.get(),
        "You are a judgment quality evaluator. Assess whether the response adequately "
            + "addresses the judgment prompt. Respond with ACCEPT if the response is adequate, "
            + "or REJECT followed by a brief reason if it is not. "
            + "Only respond with one of these two formats.",
        evaluationPrompt).trim();

    if (evaluation.toUpperCase().startsWith("ACCEPT")) {
      return new VerificationResult.Accepted();
    }

    String reason =
        evaluation.toUpperCase().startsWith("REJECT")
            ? evaluation.substring(Math.min(7, evaluation.length())).trim()
            : evaluation;

    return new VerificationResult.Rejected(
        reason.isEmpty() ? "LLM evaluation rejected the response" : reason);
  }

  private String buildEvaluationPrompt(VerificationContext context) {
    StringBuilder sb = new StringBuilder();
    sb.append("Original judgment prompt: ").append(context.target().prompt()).append("\n\n");
    sb.append("Response decision: ").append(context.decision()).append("\n\n");

    if (!context.evidence().isEmpty()) {
      sb.append("Evidence provided:\n");
      for (Evidence e : context.evidence()) {
        sb.append("  - ")
            .append(e.name())
            .append(" (")
            .append(e.type())
            .append("): ")
            .append(e.content())
            .append("\n");
      }
    }

    if (!context.target().evidenceRequirements().isEmpty()) {
      sb.append("\nRequired evidence:\n");
      for (String req : context.target().evidenceRequirements()) {
        sb.append("  - ").append(req).append("\n");
      }
    }

    return sb.toString();
  }
}

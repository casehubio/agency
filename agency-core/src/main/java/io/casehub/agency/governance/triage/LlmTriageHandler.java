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
package io.casehub.agency.governance.triage;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.core.invoke.StructuredAgentInvoker.InvocationResult;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.api.governance.TriageContext;
import io.casehub.platform.api.governance.TriageDecision;
import io.casehub.platform.api.governance.TriageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class LlmTriageHandler implements TriageHandler {

    private static final String SYSTEM_PROMPT = """
            You are analyzing a failed coordinated execution. Given the failure context, \
            decide whether to retry with modifications or abandon.

            Respond with a JSON object containing:
            - "decision": either "retry" or "abandon"
            - "modifications": a map of modification keys and values (empty map if abandoning or retrying without changes)
            - "reason": a brief explanation of your decision

            Consider:
            - Is the failure transient (timeout, temporary unavailability) or fundamental (bad data, missing capability)?
            - How many attempts have been made vs the maximum allowed?
            - Can the operation succeed with different parameters or by skipping the failing step?

            Respond ONLY with the JSON object, no other text.""";

    private final AgentProvider agentProvider;

    @Inject
    public LlmTriageHandler(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public TriageDecision evaluate(TriageContext context) {
        var userPrompt = buildUserPrompt(context);
        var config = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);

        var result = StructuredAgentInvoker.invokeRaw(agentProvider, config);

        return toDecision(result);
    }

    private static String buildUserPrompt(TriageContext context) {
        var sb = new StringBuilder();
        sb.append("## Failure Context\n\n");
        sb.append("Operation ID: ").append(context.operationId()).append('\n');
        sb.append("Operation Type: ").append(context.operationType()).append('\n');
        sb.append("Attempt: ").append(context.attemptNumber())
                .append(" of ").append(context.maxAttempts()).append('\n');
        sb.append("Failure Summary: ").append(context.failureSummary()).append('\n');

        if (!context.failureDetail().isEmpty()) {
            sb.append("\n## Failure Detail\n\n");
            context.failureDetail().forEach((k, v) ->
                    sb.append("- ").append(k).append(": ").append(v).append('\n'));
        }

        if (!context.executionContext().isEmpty()) {
            sb.append("\n## Execution Context\n\n");
            context.executionContext().forEach((k, v) ->
                    sb.append("- ").append(k).append(": ").append(v).append('\n'));
        }

        return sb.toString();
    }

    private static TriageDecision toDecision(InvocationResult<JsonNode> result) {
        if (result instanceof InvocationResult.Success<JsonNode> success) {
            return parseDecision(success.value());
        }
        if (result instanceof InvocationResult.ParseError<JsonNode> parseError) {
            return new TriageDecision.Abandon(
                    "LLM triage failed: could not parse response");
        }
        if (result instanceof InvocationResult.AgentError<JsonNode> agentError) {
            return new TriageDecision.Abandon(
                    "LLM triage failed: " + agentError.reason());
        }
        return new TriageDecision.Abandon("LLM triage failed: unknown error");
    }

    private static TriageDecision parseDecision(JsonNode node) {
        var decision = node.path("decision").asText("");
        var reason = node.path("reason").asText("no reason provided");

        if ("retry".equalsIgnoreCase(decision)) {
            Map<String, Object> modifications = new LinkedHashMap<>();
            var modsNode = node.path("modifications");
            if (modsNode.isObject()) {
                modsNode.fields().forEachRemaining(entry ->
                        modifications.put(entry.getKey(), entry.getValue().asText()));
            }
            return new TriageDecision.Retry(modifications);
        }

        return new TriageDecision.Abandon(reason);
    }
}

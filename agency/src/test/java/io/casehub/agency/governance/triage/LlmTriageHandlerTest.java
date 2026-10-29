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

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.api.governance.TriageContext;
import io.casehub.platform.api.governance.TriageDecision;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmTriageHandlerTest {

    private final AgentProvider agentProvider = mock(AgentProvider.class);
    private final LlmTriageHandler handler = new LlmTriageHandler(agentProvider);

    private TriageContext context() {
        return new TriageContext(
                "op-123", "playbook", 1, 3,
                "Step 'validate-input' failed: null pointer on field 'email'",
                Map.of("stepId", "validate-input", "errorType", "NullPointerException"),
                Map.of("caseId", "case-456", "tenancyId", "tenant-1"));
    }

    @Test
    void returnsRetryWhenLlmSuggestsModifications() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("""
                        {"decision": "retry", "modifications": {"playbook.skipStep": "validate-input"}, "reason": "skip failing validation step"}"""),
                completionEvent()
        ));

        var result = handler.evaluate(context());

        assertThat(result).isInstanceOf(TriageDecision.Retry.class);
        var retry = (TriageDecision.Retry) result;
        assertThat(retry.modifications()).containsEntry("playbook.skipStep", "validate-input");
    }

    @Test
    void returnsAbandonWhenLlmRecommendsAbandoning() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("""
                        {"decision": "abandon", "modifications": {}, "reason": "fundamental data issue, retry would not help"}"""),
                completionEvent()
        ));

        var result = handler.evaluate(context());

        assertThat(result).isInstanceOf(TriageDecision.Abandon.class);
        var abandon = (TriageDecision.Abandon) result;
        assertThat(abandon.reason()).contains("fundamental data issue");
    }

    @Test
    void fallsBackToAbandonOnLlmError() {
        when(agentProvider.invoke(any())).thenReturn(
                Multi.createFrom().failure(new RuntimeException("connection refused")));

        var result = handler.evaluate(context());

        assertThat(result).isInstanceOf(TriageDecision.Abandon.class);
        var abandon = (TriageDecision.Abandon) result;
        assertThat(abandon.reason()).contains("LLM triage failed");
    }

    @Test
    void fallsBackToAbandonOnParseError() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("I'm not sure what to do here"),
                completionEvent()
        ));

        var result = handler.evaluate(context());

        assertThat(result).isInstanceOf(TriageDecision.Abandon.class);
    }

    @Test
    void promptIncludesContextFields() {
        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        when(agentProvider.invoke(configCaptor.capture())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("""
                        {"decision": "abandon", "modifications": {}, "reason": "not recoverable"}"""),
                completionEvent()
        ));

        handler.evaluate(context());

        var config = configCaptor.getValue();
        assertThat(config.userPrompt()).contains("playbook");
        assertThat(config.userPrompt()).contains("validate-input");
        assertThat(config.userPrompt()).contains("null pointer on field 'email'");
        assertThat(config.userPrompt()).contains("NullPointerException");
    }

    @Test
    void promptIncludesAttemptInfo() {
        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        var ctx = new TriageContext("op-1", "playbook", 2, 3,
                "timeout", Map.of(), Map.of());
        when(agentProvider.invoke(configCaptor.capture())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("""
                        {"decision": "abandon", "modifications": {}, "reason": "repeated timeout"}"""),
                completionEvent()
        ));

        handler.evaluate(ctx);

        var config = configCaptor.getValue();
        assertThat(config.userPrompt()).contains("2");
        assertThat(config.userPrompt()).contains("3");
    }

    @Test
    void retryWithEmptyModificationsIsValid() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("""
                        {"decision": "retry", "modifications": {}, "reason": "transient issue, simple retry should work"}"""),
                completionEvent()
        ));

        var result = handler.evaluate(context());

        assertThat(result).isInstanceOf(TriageDecision.Retry.class);
        assertThat(((TriageDecision.Retry) result).modifications()).isEmpty();
    }

    private static AgentEvent.InvocationComplete completionEvent() {
        return new AgentEvent.InvocationComplete(
                10, 5, 0, 0, 0, 0.001, 200L, 150L, "s1", 1, false, null, null);
    }
}

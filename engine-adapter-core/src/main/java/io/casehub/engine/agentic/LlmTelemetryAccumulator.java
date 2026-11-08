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
package io.casehub.engine.agentic;

import io.casehub.agency.orchestration.loop.DecisionLoopEvent;
import io.casehub.api.spi.stigmergy.AgentLlmStatsProvider;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LlmTelemetryAccumulator implements Consumer<DecisionLoopEvent>, AgentLlmStatsProvider {

    private final UUID caseId;
    private final ConcurrentHashMap<String, LlmAccumulator> accumulators = new ConcurrentHashMap<>();

    public LlmTelemetryAccumulator(UUID caseId) {
        this.caseId = caseId;
    }

    @Override
    public void accept(DecisionLoopEvent event) {
        if (event instanceof DecisionLoopEvent.ReasoningCompleted rc) {
            accumulators.computeIfAbsent(rc.agentId(), k -> new LlmAccumulator())
                        .record(rc.tokensConsumed(), rc.latencyMs());
        }
    }

    @Override
    public AgentLlmStats getStats(UUID caseId, String agentId) {
        var acc = accumulators.get(agentId);
        if (acc == null) return AgentLlmStats.ZERO;
        return new AgentLlmStats(acc.invocationCount, acc.tokensConsumed,
                Duration.ofMillis(acc.totalLatencyMs));
    }

    private static class LlmAccumulator {
        private int invocationCount;
        private long tokensConsumed;
        private long totalLatencyMs;

        void record(long tokens, long latencyMs) {
            invocationCount++;
            tokensConsumed += tokens;
            totalLatencyMs += latencyMs;
        }
    }
}

package io.casehub.agency.orchestration.pattern;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.execution.ExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static io.casehub.agency.orchestration.pattern.Patterns.loop;
import static org.assertj.core.api.Assertions.assertThat;

class LoopBuilderTest {

    @Test
    void loopsUntilMaxIterations() {
        var count = new AtomicInteger(0);
        var agent = AgentRef.external((Object i) -> {
            count.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "loop"));
        });

        var result = loop()
                .agents(agent)
                .maxIterations(3)
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(count.get()).isEqualTo(3);
    }

    @Test
    void exitsOnCondition() {
        var count = new AtomicInteger(0);
        var agent = AgentRef.external((Object i) -> {
            count.incrementAndGet();
            return CompletableFuture.completedFuture(AgentResult.success(null, "ok"));
        });

        var result = loop()
                .agents(agent)
                .maxIterations(100)
                .exitCondition((Object s) -> count.get() >= 2)
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(count.get()).isEqualTo(2);
    }
}

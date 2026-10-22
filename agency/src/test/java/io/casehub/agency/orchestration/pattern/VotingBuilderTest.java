package io.casehub.agency.orchestration.pattern;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.execution.ExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static io.casehub.agency.orchestration.pattern.Patterns.voting;
import static org.assertj.core.api.Assertions.assertThat;

class VotingBuilderTest {

    @Test
    void aggregatesVotes() {
        var a1 = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "yes")));
        var a2 = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "no")));
        var a3 = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "yes")));

        var result = voting()
                .evaluators(a1, a2, a3)
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }
}

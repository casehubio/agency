package io.casehub.agency.agentic.pattern;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.AgentResult;
import io.casehub.agency.agentic.model.ExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static io.casehub.agency.agentic.pattern.Patterns.voting;
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

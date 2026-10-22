package io.casehub.agency.agentic.model;

import io.casehub.agency.agentic.FailurePolicy;
import io.casehub.agency.agentic.RoutingCandidate;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.aggregation.PassThrough;
import io.casehub.agency.agentic.decomposition.IdentityDecomposition;
import io.casehub.agency.agentic.routing.FirstMatchRouting;
import io.casehub.agency.agentic.termination.MaxIterationsTermination;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionModelTest {

    private final Supplier<List<RoutingCandidate>> candidates = List::of;

    @Test
    void rejectsNullRouting() {
        assertThatThrownBy(() -> new ExecutionModel<>(
                null, new IdentityDecomposition<>(), new OnExplicitDispatch<>(),
                new PassThrough<>(), new MaxIterationsTermination<>(1),
                candidates, FailurePolicy.defaults(), List.of(), "test"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("routing");
    }

    @Test
    void rejectsNullTask() {
        assertThatThrownBy(() -> new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), candidates,
                FailurePolicy.defaults(), List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("task");
    }

    @Test
    void acceptsValidConstruction() {
        var model = new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), candidates,
                FailurePolicy.defaults(), List.of(), "my task");
        assertThat(model.task()).isEqualTo("my task");
    }

    @Test
    void backendDefaultsToNullViaSecondaryConstructor() {
        var model = new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), candidates,
                FailurePolicy.defaults(), List.of(), "test", null);
        assertThat(model.backend()).isNull();
    }

    @Test
    void backendCarriedOnModel() {
        ExecutionBackend<String> backend = (m, ctx) ->
                                                   io.smallrye.mutiny.Uni.createFrom().item(new ExecutionResult.Completed("ok"));
        var model = new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true), new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(), new PassThrough<>(),
                new MaxIterationsTermination<>(1), candidates,
                FailurePolicy.defaults(), List.of(), "test", null, backend);
        assertThat(model.backend()).isSameAs(backend);
    }

}

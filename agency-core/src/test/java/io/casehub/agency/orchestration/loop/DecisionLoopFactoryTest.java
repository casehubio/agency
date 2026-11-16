package io.casehub.agency.orchestration.loop;

import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.platform.agent.budget.AutonomyBudgetPolicy;
import io.casehub.platform.agent.budget.AutonomyBudgetPool;
import io.casehub.platform.agent.budget.BudgetPriority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DecisionLoopFactoryTest {

    static final DecisionContext CTX = new DecisionContext(
        UUID.randomUUID(), "agent-1", DecisionScope.FULL_AUTONOMY,
        CommunicationMode.STIGMERGY,
        new TopologyDescriptor(TopologyType.CENTRALISED, Map.of()));

    static final ContentSummariser<ObservationEvent, DecisionOutcome> NOOP_REASONER =
        (items, prev) -> CompletableFuture.completedFuture(
            new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));

    @Test
    void create_eventDriven() {
        var registry = new MapPromptSectionRegistry(Map.of());
        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(registry, bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var loop = factory.create(descriptor, CTX, NOOP_REASONER, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        assertThat(loop).isNotNull();
        assertThat(loop.descriptor()).isEqualTo(descriptor);
        assertThat(loop.context()).isEqualTo(CTX);
    }

    @Test
    void create_tickDriven() {
        var registry = new MapPromptSectionRegistry(Map.of());
        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(registry, bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.TICK_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var loop = factory.create(descriptor, CTX, NOOP_REASONER, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        assertThat(loop).isNotNull();
    }

    @Test
    void create_demandDriven() {
        var registry = new MapPromptSectionRegistry(Map.of());
        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(registry, bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.DEMAND_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var loop = factory.create(descriptor, CTX, NOOP_REASONER, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        assertThat(loop).isNotNull();
    }

    @Test
    void create_resolvesPromptSections() {
        LoopPromptSection section = ctx -> "section-output";
        var registry = new MapPromptSectionRegistry(Map.of("test-section", section));
        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(registry, bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of("test-section"));

        var loop = factory.create(descriptor, CTX, NOOP_REASONER, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        assertThat(loop).isNotNull();
    }

    @Test
    void create_failsFastOnUnknownSection() {
        var registry = new MapPromptSectionRegistry(Map.of());
        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(registry, bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of("nonexistent"));

        assertThatIllegalArgumentException()
            .isThrownBy(() -> factory.create(descriptor, CTX, NOOP_REASONER, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {}))
            .withMessageContaining("nonexistent");
    }

    @Test
    void create_wiresBudget() {
        var registry = new MapPromptSectionRegistry(Map.of());
        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(registry, bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var loop = factory.create(descriptor, CTX, NOOP_REASONER, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        assertThat(loop.budget().callsUsed()).isEqualTo(0);
    }

    @Test
    void estimateTokens_writeBack_returnsEstimatedOutputTokens() {
        var wb = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of(), 1234L);
        assertThat(DecisionLoopFactory.estimateTokens(wb)).isEqualTo(1234L);
    }

    @Test
    void estimateTokens_writeBack_zeroWhenNotSet() {
        var wb = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        assertThat(DecisionLoopFactory.estimateTokens(wb)).isZero();
    }

    @Test
    void estimateTokens_composite_sumsChildren() {
        var wb1       = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of(), 100L);
        var wb2       = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of(), 200L);
        var composite = new DecisionOutcome.Composite(List.of(wb1, wb2));
        assertThat(DecisionLoopFactory.estimateTokens(composite)).isEqualTo(300L);
    }

    @Test
    void estimateTokens_route_returnsZero() {
        var route = new DecisionOutcome.Route(
                new io.casehub.agency.orchestration.routing.RoutingDecision.Unresolvable("none"));
        assertThat(DecisionLoopFactory.estimateTokens(route)).isZero();
    }

}

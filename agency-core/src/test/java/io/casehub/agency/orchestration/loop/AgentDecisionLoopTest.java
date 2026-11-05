package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;

import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AgentDecisionLoopTest {

    static final DecisionContext CTX = new DecisionContext(
        UUID.randomUUID(), "agent-1", DecisionScope.FULL_AUTONOMY,
        CommunicationMode.STIGMERGY,
        new TopologyDescriptor(TopologyType.CENTRALISED, Map.of()));

    @Test
    void eventDriven_collectAndTick_producesOutcome() {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        ContentSummariser<ObservationEvent, DecisionOutcome> reasoner =
            (items, prev) -> CompletableFuture.completedFuture(outcome);

        var bus = new EventStreamBus<DecisionOutcome>();
        var captured = new ArrayList<DecisionOutcome>();
        bus.subscribe(e -> true, e -> captured.add(e.payload()));

        var factory = new DecisionLoopFactory(
            new MapPromptSectionRegistry(Map.of()), bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var loop = factory.create(descriptor, CTX, reasoner, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        loop.collect(ObservationEvent.of("signal", 0.9));
        loop.tick(System.currentTimeMillis()).toCompletableFuture().join();

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).isSameAs(outcome);
    }

    @Test
    void demandDriven_tickDoesNotEmit_flushDoes() {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        ContentSummariser<ObservationEvent, DecisionOutcome> reasoner =
            (items, prev) -> CompletableFuture.completedFuture(outcome);

        var bus = new EventStreamBus<DecisionOutcome>();
        var captured = new ArrayList<DecisionOutcome>();
        bus.subscribe(e -> true, e -> captured.add(e.payload()));

        var factory = new DecisionLoopFactory(
            new MapPromptSectionRegistry(Map.of()), bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.DEMAND_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var loop = factory.create(descriptor, CTX, reasoner, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        loop.collect(ObservationEvent.of("signal", 0.9));
        loop.tick(System.currentTimeMillis()).toCompletableFuture().join();
        assertThat(captured).isEmpty();

        loop.flush().toCompletableFuture().join();
        assertThat(captured).hasSize(1);
    }

    @Test
    void budgetExhausted_tickIsNoop() {
        var callCount = new AtomicInteger(0);
        ContentSummariser<ObservationEvent, DecisionOutcome> reasoner =
            (items, prev) -> {
                callCount.incrementAndGet();
                return CompletableFuture.completedFuture(
                    new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));
            };

        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(
            new MapPromptSectionRegistry(Map.of()), bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(50, 500_000, 0.0, 1, 50_000), "test");
        var loop = factory.create(descriptor, CTX, reasoner, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), pool, () -> false, e -> {}, e -> {});

        loop.collect(ObservationEvent.of("s1", 1));
        loop.tick(System.currentTimeMillis()).toCompletableFuture().join();
        assertThat(callCount.get()).isEqualTo(1);

        loop.collect(ObservationEvent.of("s2", 2));
        loop.tick(System.currentTimeMillis()).toCompletableFuture().join();
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void budgetExhausted_flushDoesNotThrow() {
        ContentSummariser<ObservationEvent, DecisionOutcome> reasoner =
            (items, prev) -> CompletableFuture.completedFuture(
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));

        var bus = new EventStreamBus<DecisionOutcome>();
        var captured = new ArrayList<DecisionOutcome>();
        bus.subscribe(e -> true, e -> captured.add(e.payload()));
        var factory = new DecisionLoopFactory(
            new MapPromptSectionRegistry(Map.of()), bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.DEMAND_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(1, 500_000, 0.0, 1, 500_000), "test");
        pool.tryAcquire(BudgetPriority.NORMAL);
        var loop = factory.create(descriptor, CTX, reasoner, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), pool, () -> false, e -> {}, e -> {});
        loop.collect(ObservationEvent.of("s", 1));

        loop.flush().toCompletableFuture().join();
        assertThat(captured).isEmpty();
    }

    @Test
    void close_clearsAccumulatedEvents() {
        var callCount = new AtomicInteger(0);
        ContentSummariser<ObservationEvent, DecisionOutcome> reasoner =
            (items, prev) -> {
                callCount.incrementAndGet();
                return CompletableFuture.completedFuture(
                    new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));
            };

        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(
            new MapPromptSectionRegistry(Map.of()), bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.DEMAND_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var loop = factory.create(descriptor, CTX, reasoner, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        loop.collect(ObservationEvent.of("s", 1));
        loop.close();
        loop.flush().toCompletableFuture().join();
        assertThat(callCount.get()).isEqualTo(0);
    }

    @Test
    void multipleObservations_singleSummarisationCall() {
        var receivedCounts = new ArrayList<Integer>();
        ContentSummariser<ObservationEvent, DecisionOutcome> reasoner =
            (items, prev) -> {
                receivedCounts.add(items.size());
                return CompletableFuture.completedFuture(
                    new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));
            };

        var bus = new EventStreamBus<DecisionOutcome>();
        var factory = new DecisionLoopFactory(
            new MapPromptSectionRegistry(Map.of()), bus);
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.DEMAND_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, List.of());

        var loop = factory.create(descriptor, CTX, reasoner, new io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser<>(), new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "test"), () -> false, e -> {}, e -> {});
        loop.collect(ObservationEvent.of("s1", 1));
        loop.collect(ObservationEvent.of("s2", 2));
        loop.collect(ObservationEvent.of("s3", 3));
        loop.flush().toCompletableFuture().join();

        assertThat(receivedCounts).containsExactly(3);
    }
}

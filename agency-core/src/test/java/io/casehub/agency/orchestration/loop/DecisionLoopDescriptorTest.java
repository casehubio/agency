package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.BudgetPriority;

import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DecisionLoopDescriptorTest {

    @Test
    void validConstruction() {
        var descriptor = new DecisionLoopDescriptor(
            "test-loop",
            ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY,
            CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL,
            List.of("section-a", "section-b"));
        assertThat(descriptor.name()).isEqualTo("test-loop");
        assertThat(descriptor.promptSectionNames()).containsExactly("section-a", "section-b");
    }

    @Test
    void nullSectionNames_defaultsToEmpty() {
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, null);
        assertThat(descriptor.promptSectionNames()).isEmpty();
    }

    @Test
    void sectionNames_areImmutable() {
        var mutable = new ArrayList<>(List.of("a", "b"));
        var descriptor = new DecisionLoopDescriptor(
            "test", ObservationStrategy.EVENT_DRIVEN,
            DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
            BudgetPriority.NORMAL, mutable);
        mutable.add("c");
        assertThat(descriptor.promptSectionNames()).containsExactly("a", "b");
    }

    @Test
    void nullName_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DecisionLoopDescriptor(
                null, ObservationStrategy.EVENT_DRIVEN,
                DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
                BudgetPriority.NORMAL, List.of()));
    }

    @Test
    void nullObservationStrategy_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DecisionLoopDescriptor(
                "test", null,
                DecisionScope.FULL_AUTONOMY, CommunicationMode.STIGMERGY,
                BudgetPriority.NORMAL, List.of()));
    }
}

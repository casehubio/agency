package io.casehub.agency.playbook;

import io.casehub.yaml.core.orchestration.DefaultOrcCounter;
import io.casehub.yaml.core.orchestration.DefaultOrcStateMachine;
import io.casehub.yaml.core.orchestration.EventRouter;
import io.casehub.yaml.core.orchestration.OrcCounter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookProtocolPrimitivesTest {

    @Test
    void counter_tracks_open_queries() {
        OrcCounter openQueries = new DefaultOrcCounter();

        openQueries.increment();
        openQueries.increment();
        assertThat(openQueries.get()).isEqualTo(2);

        openQueries.decrement();
        assertThat(openQueries.get()).isEqualTo(1);

        openQueries.increment();
        openQueries.increment();
        assertThat(openQueries.get()).isEqualTo(3);
    }

    @Test
    void counter_threshold_fires_warning_at_limit() {
        int maxOpenQueries = 3;
        OrcCounter openQueries = new DefaultOrcCounter();
        List<String> advisories = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            openQueries.increment();
            if (openQueries.get() >= maxOpenQueries) {
                advisories.add("WARNING");
            }
        }

        assertThat(advisories).hasSize(2);
    }

    @Test
    void counter_threshold_listener_reactive_notification() {
        int maxOpenQueries = 3;
        OrcCounter openQueries = new DefaultOrcCounter();
        AtomicBoolean thresholdBreached = new AtomicBoolean(false);

        openQueries.onThresholdChange(value -> {
            if (value >= maxOpenQueries) thresholdBreached.set(true);
        });

        openQueries.increment();
        assertThat(thresholdBreached.get()).isFalse();
        openQueries.increment();
        assertThat(thresholdBreached.get()).isFalse();
        openQueries.increment();
        assertThat(thresholdBreached.get()).isTrue();
    }

    @Test
    void counter_decrements_on_response() {
        OrcCounter openQueries = new DefaultOrcCounter();
        openQueries.add(3);

        openQueries.decrement();
        assertThat(openQueries.get()).isEqualTo(2);

        openQueries.decrement();
        assertThat(openQueries.get()).isEqualTo(1);
    }

    @Test
    void state_machine_transitions_with_counter_guard() {
        OrcCounter openQueries = new DefaultOrcCounter();

        var builder = DefaultOrcStateMachine.<String>builder("request-response", "IDLE");
        builder.on("QUERY", "IDLE", "ACTIVE")
               .on("RESPONSE", "ACTIVE", "IDLE")
               .on("QUERY", "ACTIVE", "OVERLOADED", payload -> openQueries.get() >= 2)
               .on("RESPONSE", "OVERLOADED", "ACTIVE");

        var sm = builder.build();
        EventRouter<String> router = builder.buildRouter(sm);

        openQueries.increment();
        router.fire("QUERY", null);
        assertThat(sm.currentState()).isEqualTo("ACTIVE");

        openQueries.increment();
        router.fire("QUERY", null);
        assertThat(sm.currentState()).isEqualTo("OVERLOADED");

        openQueries.decrement();
        router.fire("RESPONSE", null);
        assertThat(sm.currentState()).isEqualTo("ACTIVE");
    }

    @Test
    void task_completion_counter_pattern() {
        int maxOpenCommands = 3;
        OrcCounter openCommands = new DefaultOrcCounter();
        List<String> advisories = new ArrayList<>();

        String[] events = {"COMMAND", "COMMAND", "COMMAND", "COMMAND", "DONE", "FAILURE"};

        for (String event : events) {
            switch (event) {
                case "COMMAND" -> {
                    openCommands.increment();
                    if (openCommands.get() >= maxOpenCommands) {
                        advisories.add("WARNING: " + openCommands.get() + " open commands");
                    }
                }
                case "DONE", "FAILURE", "DECLINE" -> openCommands.decrement();
            }
        }

        assertThat(openCommands.get()).isEqualTo(2);
        assertThat(advisories).hasSize(2);
    }

    @Test
    void info_advisory_when_non_query_with_open_queries() {
        OrcCounter openQueries = new DefaultOrcCounter();
        List<String> advisories = new ArrayList<>();

        openQueries.increment();
        openQueries.increment();

        String event = "INFORM";
        if (!event.equals("QUERY") && !event.equals("RESPONSE") && openQueries.get() > 0) {
            advisories.add("INFO: channel has open queries");
        }

        assertThat(advisories).containsExactly("INFO: channel has open queries");
    }
}

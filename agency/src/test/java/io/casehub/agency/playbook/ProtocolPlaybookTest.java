package io.casehub.agency.playbook;

import io.casehub.yaml.core.orchestration.DefaultOrcCounter;
import io.casehub.yaml.core.orchestration.DefaultOrcStateMachine;
import io.casehub.yaml.core.orchestration.EventRouter;
import io.casehub.yaml.core.orchestration.OrcCounter;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.catalog.ResolvedMatchCase;
import io.casehub.yaml.step.catalog.ResolvedStep;
import io.casehub.yaml.step.catalog.Walker;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolPlaybookTest {

    private static final CompositePluginRegistry REGISTRY = new CompositePluginRegistry();

    static {
        for (String action : List.of("counter-add", "counter-get", "advisory")) {
            REGISTRY.register(new Definition(action, null, Map.of(), Map.of(),
                    Portability.BOTH, (params, services) -> Result.of(Map.of()), null));
        }
    }

    // ── Parse validation ──

    @Test
    void request_response_playbook_parses_through_walker() {
        var steps = loadPlaybookSteps("playbook-templates/request-response.playbook.yaml");
        var resolved = Walker.resolve(steps, REGISTRY);
        assertThat(resolved).hasSize(1);

        var evaluate = resolved.getFirst();
        assertThat(evaluate.decorators()).containsKeys("from", "loop");
        assertThat(evaluate).isInstanceOf(ResolvedStep.MatchStep.class);

        var matchStep = (ResolvedStep.MatchStep) evaluate;
        assertThat(matchStep.cases()).hasSize(3);
        assertThat(matchStep.cases().get(0).pattern().toString()).contains("QUERY");
        assertThat(matchStep.cases().get(1).pattern().toString()).contains("RESPONSE");
    }

    @Test
    void task_completion_playbook_parses_through_walker() {
        var steps = loadPlaybookSteps("playbook-templates/task-completion.playbook.yaml");
        var resolved = Walker.resolve(steps, REGISTRY);
        assertThat(resolved).hasSize(1);

        var matchStep = (ResolvedStep.MatchStep) resolved.getFirst();
        assertThat(matchStep.cases()).hasSize(5);
    }

    // ── Sequence validation: REQUEST_RESPONSE ──

    @Test
    void request_response_sequence_warns_at_threshold() {
        int maxOpenQueries = 3;
        OrcCounter openQueries = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new RequestResponseEvaluator(openQueries, maxOpenQueries, advisories);

        evaluator.onEvent("QUERY");   // count=1, no warning
        evaluator.onEvent("QUERY");   // count=2, no warning
        evaluator.onEvent("QUERY");   // count=3, WARNING

        assertThat(advisories).hasSize(1);
        assertThat(advisories.getFirst().severity()).isEqualTo("WARNING");
        assertThat(openQueries.get()).isEqualTo(3);
    }

    @Test
    void request_response_sequence_response_clears_and_resets() {
        int maxOpenQueries = 3;
        OrcCounter openQueries = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new RequestResponseEvaluator(openQueries, maxOpenQueries, advisories);

        evaluator.onEvent("QUERY");   // count=1
        evaluator.onEvent("QUERY");   // count=2
        evaluator.onEvent("RESPONSE");// count=1
        evaluator.onEvent("QUERY");   // count=2
        evaluator.onEvent("QUERY");   // count=3, WARNING

        assertThat(advisories).hasSize(1);
        assertThat(openQueries.get()).isEqualTo(3);
    }

    @Test
    void request_response_sequence_interleaved_responses_prevent_warning() {
        int maxOpenQueries = 3;
        OrcCounter openQueries = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new RequestResponseEvaluator(openQueries, maxOpenQueries, advisories);

        evaluator.onEvent("QUERY");    // count=1
        evaluator.onEvent("RESPONSE"); // count=0
        evaluator.onEvent("QUERY");    // count=1
        evaluator.onEvent("RESPONSE"); // count=0
        evaluator.onEvent("QUERY");    // count=1

        assertThat(advisories).isEmpty();
        assertThat(openQueries.get()).isEqualTo(1);
    }

    @Test
    void request_response_info_on_non_query_with_open_queries() {
        int maxOpenQueries = 3;
        OrcCounter openQueries = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new RequestResponseEvaluator(openQueries, maxOpenQueries, advisories);

        evaluator.onEvent("QUERY");   // count=1
        evaluator.onEvent("INFORM");  // non-query with open queries → INFO

        assertThat(advisories).hasSize(1);
        assertThat(advisories.getFirst().severity()).isEqualTo("INFO");
    }

    @Test
    void request_response_no_info_when_no_open_queries() {
        int maxOpenQueries = 3;
        OrcCounter openQueries = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new RequestResponseEvaluator(openQueries, maxOpenQueries, advisories);

        evaluator.onEvent("INFORM"); // no open queries → no advisory

        assertThat(advisories).isEmpty();
    }

    @Test
    void request_response_full_event_stream() {
        int maxOpenQueries = 3;
        OrcCounter openQueries = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new RequestResponseEvaluator(openQueries, maxOpenQueries, advisories);

        // Sequence: Q Q I Q Q R Q I
        evaluator.onEvent("QUERY");    // count=1
        evaluator.onEvent("QUERY");    // count=2
        evaluator.onEvent("INFORM");   // INFO (open queries > 0)
        evaluator.onEvent("QUERY");    // count=3, WARNING
        evaluator.onEvent("QUERY");    // count=4, WARNING
        evaluator.onEvent("RESPONSE"); // count=3
        evaluator.onEvent("QUERY");    // count=4, WARNING
        evaluator.onEvent("INFORM");   // INFO (open queries > 0)

        assertThat(advisories).extracting(Advisory::severity)
                .containsExactly("INFO", "WARNING", "WARNING", "WARNING", "INFO");
    }

    // ── Sequence validation: TASK_COMPLETION ──

    @Test
    void task_completion_sequence_warns_at_threshold() {
        int maxOpenCommands = 3;
        OrcCounter openCommands = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new TaskCompletionEvaluator(openCommands, maxOpenCommands, advisories);

        evaluator.onEvent("COMMAND");  // count=1
        evaluator.onEvent("COMMAND");  // count=2
        evaluator.onEvent("COMMAND");  // count=3, WARNING

        assertThat(advisories).hasSize(1);
        assertThat(advisories.getFirst().severity()).isEqualTo("WARNING");
    }

    @Test
    void task_completion_done_failure_decline_all_decrement() {
        int maxOpenCommands = 3;
        OrcCounter openCommands = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new TaskCompletionEvaluator(openCommands, maxOpenCommands, advisories);

        evaluator.onEvent("COMMAND");  // count=1
        evaluator.onEvent("COMMAND");  // count=2
        evaluator.onEvent("COMMAND");  // count=3, WARNING
        evaluator.onEvent("DONE");     // count=2
        evaluator.onEvent("COMMAND");  // count=3, WARNING
        evaluator.onEvent("FAILURE");  // count=2
        evaluator.onEvent("COMMAND");  // count=3, WARNING
        evaluator.onEvent("DECLINE");  // count=2

        assertThat(advisories).hasSize(3);
        assertThat(openCommands.get()).isEqualTo(2);
    }

    @Test
    void task_completion_full_event_stream() {
        int maxOpenCommands = 3;
        OrcCounter openCommands = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var evaluator = new TaskCompletionEvaluator(openCommands, maxOpenCommands, advisories);

        // Sequence: C C D C C C F C
        evaluator.onEvent("COMMAND");  // count=1
        evaluator.onEvent("COMMAND");  // count=2
        evaluator.onEvent("DONE");     // count=1
        evaluator.onEvent("COMMAND");  // count=2
        evaluator.onEvent("COMMAND");  // count=3, WARNING
        evaluator.onEvent("COMMAND");  // count=4, WARNING
        evaluator.onEvent("FAILURE");  // count=3
        evaluator.onEvent("COMMAND");  // count=4, WARNING

        assertThat(advisories).extracting(Advisory::severity)
                .containsExactly("WARNING", "WARNING", "WARNING");
        assertThat(openCommands.get()).isEqualTo(4);
    }

    // ── State machine + counter composition for sequence detection ──

    @Test
    void state_machine_detects_query_overload_sequence() {
        OrcCounter openQueries = new DefaultOrcCounter();
        List<Advisory> advisories = new ArrayList<>();

        var builder = DefaultOrcStateMachine.<String>builder("rr-protocol", "MONITORING");
        builder.on("QUERY", "MONITORING", "MONITORING")
               .on("RESPONSE", "MONITORING", "MONITORING");
        var sm = builder.build();

        sm.onEnter("MONITORING", () -> {});

        var router = builder.buildRouter(sm);

        String[] eventStream = {"QUERY", "QUERY", "RESPONSE", "QUERY", "QUERY", "QUERY"};

        for (String event : eventStream) {
            switch (event) {
                case "QUERY" -> {
                    openQueries.increment();
                    router.fire(event, null);
                    if (openQueries.get() >= 3) {
                        advisories.add(new Advisory("WARNING", "INVESTIGATE",
                                "unanswered queries exceed threshold (count=" + openQueries.get() + ")"));
                    }
                }
                case "RESPONSE" -> {
                    if (openQueries.get() > 0) openQueries.decrement();
                    router.fire(event, null);
                }
            }
        }

        assertThat(advisories).hasSize(2);
        assertThat(advisories).extracting(Advisory::message)
                .allMatch(m -> m.contains("threshold"));
        assertThat(openQueries.get()).isEqualTo(4);
    }

    // ── Helpers ──

    record Advisory(String severity, String action, String message) {}

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadPlaybookSteps(String resourcePath) {
        Yaml yaml = new Yaml();
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertThat(is).as("Resource must exist: " + resourcePath).isNotNull();

        List<Object> documents = new ArrayList<>();
        for (Object doc : yaml.loadAll(is)) {
            if (doc != null) documents.add(doc);
        }

        assertThat(documents).as("Playbook must have at least 2 YAML documents")
                .hasSizeGreaterThanOrEqualTo(2);

        Object lastDoc = documents.getLast();
        if (lastDoc instanceof List<?>) {
            return (List<Map<String, Object>>) lastDoc;
        }
        throw new AssertionError("Last document must be a step list, got: " + lastDoc.getClass());
    }

    static class RequestResponseEvaluator {
        private final OrcCounter openQueries;
        private final int maxOpenQueries;
        private final List<Advisory> advisories;

        RequestResponseEvaluator(OrcCounter openQueries, int maxOpenQueries, List<Advisory> advisories) {
            this.openQueries = openQueries;
            this.maxOpenQueries = maxOpenQueries;
            this.advisories = advisories;
        }

        void onEvent(String eventType) {
            switch (eventType) {
                case "QUERY" -> {
                    openQueries.increment();
                    if (openQueries.get() >= maxOpenQueries) {
                        advisories.add(new Advisory("WARNING", "INVESTIGATE",
                                "unanswered queries exceed threshold"));
                    }
                }
                case "RESPONSE" -> {
                    if (openQueries.get() > 0) openQueries.decrement();
                }
                default -> {
                    if (openQueries.get() > 0) {
                        advisories.add(new Advisory("INFO", "LOG",
                                "channel has open queries awaiting response"));
                    }
                }
            }
        }
    }

    static class TaskCompletionEvaluator {
        private final OrcCounter openCommands;
        private final int maxOpenCommands;
        private final List<Advisory> advisories;

        TaskCompletionEvaluator(OrcCounter openCommands, int maxOpenCommands, List<Advisory> advisories) {
            this.openCommands = openCommands;
            this.maxOpenCommands = maxOpenCommands;
            this.advisories = advisories;
        }

        void onEvent(String eventType) {
            switch (eventType) {
                case "COMMAND" -> {
                    openCommands.increment();
                    if (openCommands.get() >= maxOpenCommands) {
                        advisories.add(new Advisory("WARNING", "INVESTIGATE",
                                "incomplete tasks exceed threshold"));
                    }
                }
                case "DONE", "FAILURE", "DECLINE" -> {
                    if (openCommands.get() > 0) openCommands.decrement();
                }
            }
        }
    }
}

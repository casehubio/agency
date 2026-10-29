package io.casehub.agency.playbook;

import io.casehub.yaml.core.orchestration.DefaultOrcCounter;
import io.casehub.yaml.core.orchestration.DefaultOrcStateMachine;
import io.casehub.yaml.core.orchestration.OrcCounter;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
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
        for (String action : List.of("counter-add", "counter-get", "counter-set", "state-set", "advisory")) {
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

    @Test
    void contribution_required_playbook_parses_through_walker() {
        var steps    = loadPlaybookSteps("playbook-templates/contribution-required.playbook.yaml");
        var resolved = Walker.resolve(steps, REGISTRY);
        assertThat(resolved).hasSize(1);

        var evaluate = resolved.getFirst();
        assertThat(evaluate.decorators()).containsKeys("from", "loop");
        assertThat(evaluate).isInstanceOf(ResolvedStep.MatchStep.class);

        var matchStep = (ResolvedStep.MatchStep) evaluate;
        assertThat(matchStep.cases()).hasSize(2);
        assertThat(matchStep.cases().get(0).pattern().toString()).contains("EVENT");
    }

    @Test
    void round_robin_playbook_parses_through_walker() {
        var steps    = loadPlaybookSteps("playbook-templates/round-robin.playbook.yaml");
        var resolved = Walker.resolve(steps, REGISTRY);
        assertThat(resolved).hasSize(1);

        var evaluate = resolved.getFirst();
        assertThat(evaluate.decorators()).containsKeys("from", "loop");
        assertThat(evaluate).isInstanceOf(ResolvedStep.MatchStep.class);

        var matchStep = (ResolvedStep.MatchStep) evaluate;
        assertThat(matchStep.cases()).hasSize(2);
        assertThat(matchStep.cases().get(0).pattern().toString()).contains("EVENT");
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
// ── Sequence validation: CONTRIBUTION_REQUIRED ──

    @Test
    void contribution_required_warns_at_consecutive_threshold() {
        int            maxConsecutive = 2;
        List<Advisory> advisories     = new ArrayList<>();

        var evaluator = new ContributionRequiredEvaluator(maxConsecutive, advisories);

        evaluator.onEvent("alice", "INFORM");  // consecutive=1, no warning
        evaluator.onEvent("alice", "INFORM");  // consecutive=2, WARNING

        assertThat(advisories).hasSize(1);
        assertThat(advisories.getFirst().severity()).isEqualTo("WARNING");
    }

    @Test
    void contribution_required_sender_change_resets_counter() {
        int            maxConsecutive = 3;
        List<Advisory> advisories     = new ArrayList<>();

        var evaluator = new ContributionRequiredEvaluator(maxConsecutive, advisories);

        evaluator.onEvent("alice", "INFORM");  // consecutive=1
        evaluator.onEvent("alice", "INFORM");  // consecutive=2
        evaluator.onEvent("bob", "INFORM");    // consecutive=1 (reset)
        evaluator.onEvent("alice", "INFORM");  // consecutive=1
        evaluator.onEvent("alice", "INFORM");  // consecutive=2

        assertThat(advisories).isEmpty();
    }

    @Test
    void contribution_required_skips_event_type_messages() {
        int            maxConsecutive = 2;
        List<Advisory> advisories     = new ArrayList<>();

        var evaluator = new ContributionRequiredEvaluator(maxConsecutive, advisories);

        evaluator.onEvent("alice", "INFORM");  // consecutive=1
        evaluator.onEvent("system", "EVENT");  // skipped
        evaluator.onEvent("alice", "INFORM");  // consecutive=2, WARNING

        assertThat(advisories).hasSize(1);
    }

    @Test
    void contribution_required_no_warning_with_alternating_senders() {
        int            maxConsecutive = 2;
        List<Advisory> advisories     = new ArrayList<>();

        var evaluator = new ContributionRequiredEvaluator(maxConsecutive, advisories);

        evaluator.onEvent("alice", "INFORM");
        evaluator.onEvent("bob", "QUERY");
        evaluator.onEvent("alice", "RESPONSE");
        evaluator.onEvent("bob", "INFORM");

        assertThat(advisories).isEmpty();
    }

    @Test
    void contribution_required_full_event_stream() {
        int            maxConsecutive = 3;
        List<Advisory> advisories     = new ArrayList<>();

        var evaluator = new ContributionRequiredEvaluator(maxConsecutive, advisories);

        // alice×3, EVENT, alice×1, bob×1, alice×3
        evaluator.onEvent("alice", "INFORM");  // consecutive=1
        evaluator.onEvent("alice", "QUERY");   // consecutive=2
        evaluator.onEvent("alice", "INFORM");  // consecutive=3, WARNING
        evaluator.onEvent("system", "EVENT");  // skipped
        evaluator.onEvent("alice", "INFORM");  // consecutive=4, WARNING (still alice)
        evaluator.onEvent("bob", "RESPONSE");  // consecutive=1 (reset)
        evaluator.onEvent("alice", "INFORM");  // consecutive=1
        evaluator.onEvent("alice", "QUERY");   // consecutive=2
        evaluator.onEvent("alice", "INFORM");  // consecutive=3, WARNING

        assertThat(advisories).extracting(Advisory::severity)
                              .containsExactly("WARNING", "WARNING", "WARNING");
    }
// ── Sequence validation: ROUND_ROBIN ──

    @Test
    void round_robin_no_warning_when_in_order() {
        List<String>   participants = List.of("alice", "bob", "carol");
        List<Advisory> advisories   = new ArrayList<>();

        var evaluator = new RoundRobinEvaluator(participants, advisories);

        evaluator.onEvent("alice", "INFORM");
        evaluator.onEvent("bob", "INFORM");
        evaluator.onEvent("carol", "INFORM");
        evaluator.onEvent("alice", "INFORM");

        assertThat(advisories).isEmpty();
    }

    @Test
    void round_robin_warns_when_out_of_order() {
        List<String>   participants = List.of("alice", "bob", "carol");
        List<Advisory> advisories   = new ArrayList<>();

        var evaluator = new RoundRobinEvaluator(participants, advisories);

        evaluator.onEvent("alice", "INFORM");
        evaluator.onEvent("carol", "INFORM"); // expected bob → ADVISORY

        assertThat(advisories).hasSize(1);
        assertThat(advisories.getFirst().severity()).isEqualTo("ADVISORY");
    }

    @Test
    void round_robin_skips_event_type_messages() {
        List<String>   participants = List.of("alice", "bob");
        List<Advisory> advisories   = new ArrayList<>();

        var evaluator = new RoundRobinEvaluator(participants, advisories);

        evaluator.onEvent("alice", "INFORM");
        evaluator.onEvent("system", "EVENT");  // skipped
        evaluator.onEvent("bob", "INFORM");    // expected bob, got bob → OK

        assertThat(advisories).isEmpty();
    }

    @Test
    void round_robin_ignores_unknown_senders() {
        List<String>   participants = List.of("alice", "bob");
        List<Advisory> advisories   = new ArrayList<>();

        var evaluator = new RoundRobinEvaluator(participants, advisories);

        evaluator.onEvent("alice", "INFORM");
        evaluator.onEvent("external", "INFORM"); // not a participant → ignored
        evaluator.onEvent("bob", "INFORM");       // expected bob → OK

        assertThat(advisories).isEmpty();
    }

    @Test
    void round_robin_no_warning_on_first_message() {
        List<String>   participants = List.of("alice", "bob", "carol");
        List<Advisory> advisories   = new ArrayList<>();

        var evaluator = new RoundRobinEvaluator(participants, advisories);

        evaluator.onEvent("carol", "INFORM"); // first message, no prior sender

        assertThat(advisories).isEmpty();
    }

    @Test
    void round_robin_wraps_around_participant_list() {
        List<String>   participants = List.of("alice", "bob", "carol");
        List<Advisory> advisories   = new ArrayList<>();

        var evaluator = new RoundRobinEvaluator(participants, advisories);

        evaluator.onEvent("carol", "INFORM");  // first, no warning
        evaluator.onEvent("alice", "INFORM");  // expected: alice (carol→alice wraps) → OK
        evaluator.onEvent("bob", "INFORM");    // expected: bob → OK
        evaluator.onEvent("carol", "INFORM");  // expected: carol → OK

        assertThat(advisories).isEmpty();
    }

    @Test
    void round_robin_full_event_stream() {
        List<String>   participants = List.of("alice", "bob", "carol");
        List<Advisory> advisories   = new ArrayList<>();

        var evaluator = new RoundRobinEvaluator(participants, advisories);

        evaluator.onEvent("alice", "INFORM");   // first → OK
        evaluator.onEvent("bob", "INFORM");     // expected bob → OK
        evaluator.onEvent("alice", "INFORM");   // expected carol → ADVISORY
        evaluator.onEvent("system", "EVENT");   // skipped
        evaluator.onEvent("bob", "INFORM");     // expected bob (after alice) → OK
        evaluator.onEvent("external", "QUERY"); // unknown → ignored
        evaluator.onEvent("alice", "INFORM");   // expected carol → ADVISORY

        assertThat(advisories).extracting(Advisory::severity)
                              .containsExactly("ADVISORY", "ADVISORY");
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

    static class ContributionRequiredEvaluator {
        private final int            maxConsecutive;
        private final List<Advisory> advisories;
        private       String         lastSender;
        private       int            consecutiveCount;

        ContributionRequiredEvaluator(int maxConsecutive, List<Advisory> advisories) {
            this.maxConsecutive = maxConsecutive;
            this.advisories     = advisories;
        }

        void onEvent(String sender, String eventType) {
            if ("EVENT".equals(eventType)) {return;}

            if (sender.equals(lastSender)) {
                consecutiveCount++;
            } else {
                consecutiveCount = 1;
                lastSender       = sender;
            }

            if (consecutiveCount >= maxConsecutive) {
                advisories.add(new Advisory("WARNING", "LOG",
                                            "sender has sent too many consecutive messages without contributions from others"));
            }
        }
    }

    static class RoundRobinEvaluator {
        private final List<String>   participants;
        private final List<Advisory> advisories;
        private       String         lastParticipantSender;

        RoundRobinEvaluator(List<String> participants, List<Advisory> advisories) {
            this.participants = participants;
            this.advisories   = advisories;
        }

        void onEvent(String sender, String eventType) {
            if ("EVENT".equals(eventType)) {return;}
            if (!participants.contains(sender)) {return;}

            if (lastParticipantSender != null) {
                int    lastIdx  = participants.indexOf(lastParticipantSender);
                String expected = participants.get((lastIdx + 1) % participants.size());
                if (!sender.equals(expected)) {
                    advisories.add(new Advisory("ADVISORY", "LOG",
                                                "sender is out of expected round-robin order"));
                }
            }

            lastParticipantSender = sender;
        }
    }


}

package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.agency.core.yaml.spec.cognition.CognitionDefinition;
import io.casehub.agency.core.yaml.spec.cognition.DriveConfigSpec;
import io.casehub.agency.core.yaml.spec.cognition.GoalEscalationConfigSpec;
import io.casehub.agency.core.yaml.spec.cognition.MentalModelConfigSpec;
import io.casehub.agency.core.yaml.spec.cognition.MoodConfigSpec;



import io.casehub.agency.core.yaml.spec.world.AffordanceSpec;
import io.casehub.agency.core.yaml.spec.world.EntitySpec;
import io.casehub.agency.core.yaml.spec.world.ObservationSectionSpec;
import io.casehub.agency.core.yaml.spec.world.RendererSpec;
import io.casehub.yaml.jackson.YamlMappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KebabCaseAliasTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class PatternDefinitionKebab {

        @Test
        void swarm_with_kebab_case_fields() throws Exception {
            var yaml = """
                    type: swarm
                    name: test-swarm
                    description: test
                    task: analyse
                    agents:
                      - type: worker
                        name: w1
                    coordination-model: emergent
                    invocation-policy:
                      max-calls: 100
                      max-tokens: 50000
                      reserve-fraction: 0.2
                      max-calls-per-agent: 10
                      max-tokens-per-agent: 5000
                    pre-filter-threshold: 3
                    """;
            var spec = mapper.readValue(yaml, PatternDefinition.class);
            assertThat(spec).isInstanceOf(PatternDefinition.Swarm.class);
            var swarm = (PatternDefinition.Swarm) spec;
            assertThat(swarm.coordinationModel()).isEqualTo("emergent");
            assertThat(swarm.preFilterThreshold()).isEqualTo(3);
            assertThat(swarm.invocationPolicy().maxCalls()).isEqualTo(100);
            assertThat(swarm.invocationPolicy().maxTokens()).isEqualTo(50000);
            assertThat(swarm.invocationPolicy().reserveFraction()).isEqualTo(0.2);
            assertThat(swarm.invocationPolicy().maxCallsPerAgent()).isEqualTo(10);
            assertThat(swarm.invocationPolicy().maxTokensPerAgent()).isEqualTo(5000);
        }

        @Test
        void loop_with_kebab_case_fields() throws Exception {
            var yaml = """
                    type: loop
                    name: test-loop
                    description: test
                    task: iterate
                    agents:
                      - type: worker
                        name: w1
                    max-iterations: 5
                    exit-condition: "done == true"
                    """;
            var spec = mapper.readValue(yaml, PatternDefinition.class);
            var loop = (PatternDefinition.Loop) spec;
            assertThat(loop.maxIterations()).isEqualTo(5);
            assertThat(loop.exitCondition()).isEqualTo("done == true");
        }

        @Test
        void debate_with_kebab_case_fields() throws Exception {
            var yaml = """
                    type: debate
                    name: test-debate
                    description: test
                    task: discuss
                    agents:
                      - type: worker
                        name: w1
                    max-rounds: 3
                    """;
            var spec = mapper.readValue(yaml, PatternDefinition.class);
            var debate = (PatternDefinition.Debate) spec;
            assertThat(debate.maxRounds()).isEqualTo(3);
        }

        @Test
        void htn_with_kebab_case_fields() throws Exception {
            var yaml = """
                    type: htn
                    name: test-htn
                    description: test
                    task: plan
                    agents:
                      - type: worker
                        name: w1
                    root-task:
                      type: primitive
                      name: analyse
                    """;
            var spec = mapper.readValue(yaml, PatternDefinition.class);
            var htn = (PatternDefinition.Htn) spec;
            assertThat(htn.rootTask()).isNotNull();
            assertThat(htn.rootTask()).isInstanceOf(TaskNodeSpec.Primitive.class);
        }
    }

    @Nested
    class GovernanceKebab {

        @Test
        void governance_with_kebab_case_fields() throws Exception {
            var yaml = """
                    trust-routing:
                      threshold: 0.8
                      minimum-observations: 20
                      borderline-margin: 0.15
                      blend-factor: 0.7
                    cbr-routing:
                      success-weight: 1.0
                      gate-expired-weight: 0.5
                      gate-rejected-weight: 0.3
                      failure-weight: 0.0
                    attestation:
                      observer: quality-auditor
                      capability-tag: compliance
                    oversight:
                      classifier: risk-classifier
                      reversible: true
                      candidate-groups:
                        - managers
                    """;
            var spec = mapper.readValue(yaml, GovernanceSpec.class);
            assertThat(spec.trustRouting().minimumObservations()).isEqualTo(20);
            assertThat(spec.trustRouting().borderlineMargin()).isEqualTo(0.15);
            assertThat(spec.trustRouting().blendFactor()).isEqualTo(0.7);
            assertThat(spec.cbrRouting().successWeight()).isEqualTo(1.0);
            assertThat(spec.cbrRouting().gateExpiredWeight()).isEqualTo(0.5);
            assertThat(spec.cbrRouting().gateRejectedWeight()).isEqualTo(0.3);
            assertThat(spec.cbrRouting().failureWeight()).isEqualTo(0.0);
            assertThat(spec.attestation().capabilityTag()).isEqualTo("compliance");
            assertThat(spec.oversight().candidateGroups()).containsExactly("managers");
        }
    }

    @Nested
    class TerminationKebab {

        @Test
        void judge_convergence_with_kebab_case() throws Exception {
            var yaml = """
                    type: judge-convergence
                    judge:
                      type: worker
                      name: judge-agent
                    max-iterations: 10
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            var jc = (TerminationSpec.JudgeConvergence) spec;
            assertThat(jc.maxIterations()).isEqualTo(10);
        }

        @Test
        void all_agreed_with_kebab_case() throws Exception {
            var yaml = """
                    type: all-agreed
                    resolved-statuses:
                      - DONE
                      - SKIPPED
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            var aa = (TerminationSpec.AllAgreed) spec;
            assertThat(aa.resolvedStatuses()).containsExactlyInAnyOrder("DONE", "SKIPPED");
        }

        @Test
        void supervisor_with_kebab_case() throws Exception {
            var yaml = """
                    type: supervisor
                    role: moderator
                    signal-type: END_DEBATE
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            var sup = (TerminationSpec.Supervisor) spec;
            assertThat(sup.signalType()).isEqualTo("END_DEBATE");
        }

        @Test
        void contested_with_kebab_case() throws Exception {
            var yaml = """
                    type: contested
                    max-dispute-rounds: 5
                    """;
            var spec = mapper.readValue(yaml, TerminationSpec.class);
            var c = (TerminationSpec.Contested) spec;
            assertThat(c.maxDisputeRounds()).isEqualTo(5);
        }
    }

    @Nested
    class JudgmentKebab {

        @Test
        void single_caller_with_kebab_case() throws Exception {
            var yaml = """
                    trigger:
                      type: always-yield
                    caller:
                      type: single
                      caller-name: reviewer
                    """;
            var spec = mapper.readValue(yaml, JudgmentSpec.class);
            var single = (JudgmentSpec.CallerSpec.Single) spec.caller();
            assertThat(single.callerName()).isEqualTo("reviewer");
        }

        @Test
        void fan_out_with_kebab_case() throws Exception {
            var yaml = """
                    trigger:
                      type: always-yield
                    caller:
                      type: fan-out
                      caller-names:
                        - reviewer-a
                        - reviewer-b
                      agreement:
                        type: threshold
                        min-agreements: 2
                    """;
            var spec = mapper.readValue(yaml, JudgmentSpec.class);
            var fanOut = (JudgmentSpec.CallerSpec.FanOut) spec.caller();
            assertThat(fanOut.callerNames()).containsExactly("reviewer-a", "reviewer-b");
            var threshold = (JudgmentSpec.AgreementSpec.Threshold) fanOut.agreement();
            assertThat(threshold.minAgreements()).isEqualTo(2);
        }
    }

    @Nested
    class TopologyKebab {

        @Test
        void distributed_with_kebab_case() throws Exception {
            var yaml = """
                    pattern: distributed
                    convergence-threshold: 0.85
                    budget:
                      max-tokens-per-tick: 1000
                      max-concurrent-calls: 5
                    """;
            var spec = mapper.readValue(yaml, TopologySpec.class);
            var dist = (TopologySpec.Distributed) spec;
            assertThat(dist.convergenceThreshold()).isEqualTo(0.85);
            assertThat(dist.budget().maxTokensPerTick()).isEqualTo(1000);
            assertThat(dist.budget().maxConcurrentCalls()).isEqualTo(5);
        }

        @Test
        void hive_with_kebab_case_role_policy() throws Exception {
            var yaml = """
                    pattern: hive
                    roles:
                      - analyst
                    policy:
                      analyst:
                        min-agents: 2
                        max-agents: 5
                        incompatible-with:
                          - manager
                    """;
            var spec = mapper.readValue(yaml, TopologySpec.class);
            var hive = (TopologySpec.Hive) spec;
            var policy = hive.policy().get("analyst");
            assertThat(policy.minAgents()).isEqualTo(2);
            assertThat(policy.maxAgents()).isEqualTo(5);
            assertThat(policy.incompatibleWith()).containsExactly("manager");
        }
    }

    @Nested
    class CallerConfigKebab {

        @Test
        void human_caller_with_kebab_case() throws Exception {
            var yaml = """
                    type: human
                    title: Review required
                    title-expression: "case.name + ' review'"
                    claim-deadline-hours: 24
                    scope-expression: "org.id"
                    template-ref: review-template
                    payload-type: document
                    quorum:
                      instances: 3
                      required: 2
                      on-threshold-reached: CANCEL
                      allow-same-assignee: false
                    """;
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            var human = (CallerConfigSpec.Human) spec;
            assertThat(human.titleExpression()).isEqualTo("case.name + ' review'");
            assertThat(human.claimDeadlineHours()).isEqualTo(24);
            assertThat(human.scopeExpression()).isEqualTo("org.id");
            assertThat(human.templateRef()).isEqualTo("review-template");
            assertThat(human.payloadType()).isEqualTo("document");
            assertThat(human.quorum().onThresholdReached().name()).isEqualTo("CANCEL");
            assertThat(human.quorum().allowSameAssignee()).isFalse();
        }

        @Test
        void llm_caller_with_kebab_case() throws Exception {
            var yaml = """
                    type: llm
                    model-id: claude-sonnet
                    model-name: Claude Sonnet
                    system-prompt: You are a reviewer.
                    """;
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            var llm = (CallerConfigSpec.Llm) spec;
            assertThat(llm.modelId()).isEqualTo("claude-sonnet");
            assertThat(llm.modelName()).isEqualTo("Claude Sonnet");
            assertThat(llm.systemPrompt()).isEqualTo("You are a reviewer.");
        }
    }

    @Nested
    class CognitionKebab {

        @Test
        void cognition_definition_with_kebab_case() throws Exception {
            var yaml = """
                    user-model:
                      min-signals-for-synthesis: 10
                      synthesis-cooldown: PT5M
                    strategy-learning:
                      min-signals-for-conversation-case: 5
                      min-cases-for-reflection: 3
                    mental-model:
                      belief-half-life: PT24H
                      confidence-floor: 0.3
                    goal-proposal:
                      proposal-threshold: 0.7
                    goal-escalation:
                      escalation-salience-threshold: 0.8
                    norm-detection:
                      min-observations-for-norm: 5
                    collective-goal:
                      alignment-threshold: 0.6
                    cognitive-goal:
                      case-creation-threshold: 0.5
                    """;
            var spec = mapper.readValue(yaml, CognitionDefinition.class);
            assertThat(spec.userModel()).isNotNull();
            assertThat(spec.strategyLearning()).isNotNull();
            assertThat(spec.mentalModel()).isNotNull();
            assertThat(spec.goalProposal()).isNotNull();
            assertThat(spec.goalEscalation()).isNotNull();
            assertThat(spec.normDetection()).isNotNull();
            assertThat(spec.collectiveGoal()).isNotNull();
            assertThat(spec.cognitiveGoal()).isNotNull();
        }

        @Test
        void personality_evolution_alias_preserved() throws Exception {
            var yaml = """
                    personality-evolution:
                      reflection-period: PT1H
                    """;
            var spec = mapper.readValue(yaml, CognitionDefinition.class);
            assertThat(spec.personality()).isNotNull();
            assertThat(spec.personality().reflectionPeriod()).isNotNull();
        }

        @Test
        void personality_shorthand_still_works() throws Exception {
            var yaml = """
                    personality:
                      reflection-period: PT1H
                    """;
            var spec = mapper.readValue(yaml, CognitionDefinition.class);
            assertThat(spec.personality()).isNotNull();
        }

        @Test
        void drive_config_with_kebab_case() throws Exception {
            var yaml = """
                    axis-weights:
                      CURIOSITY: 0.8
                    change-threshold: 0.1
                    mood-pleasure-modulation: 0.5
                    mood-arousal-modulation: 0.3
                    personality-modulation-strength: 0.7
                    max-intensity: 1.0
                    min-intensity: 0.0
                    affiliation-decay-threshold: 0.2
                    affiliation-stale-duration: PT24H
                    autonomy-confidence-floor: 0.4
                    narrative-modulation-strength: 0.6
                    """;
            var spec = mapper.readValue(yaml, DriveConfigSpec.class);
            assertThat(spec.changeThreshold()).isEqualTo(0.1);
            assertThat(spec.moodPleasureModulation()).isEqualTo(0.5);
            assertThat(spec.moodArousalModulation()).isEqualTo(0.3);
            assertThat(spec.personalityModulationStrength()).isEqualTo(0.7);
            assertThat(spec.maxIntensity()).isEqualTo(1.0);
            assertThat(spec.minIntensity()).isEqualTo(0.0);
            assertThat(spec.affiliationDecayThreshold()).isEqualTo(0.2);
            assertThat(spec.autonomyConfidenceFloor()).isEqualTo(0.4);
            assertThat(spec.narrativeModulationStrength()).isEqualTo(0.6);
        }

        @Test
        void mood_config_with_kebab_case() throws Exception {
            var yaml = """
                    decay-time-constant: PT10S
                    max-displacement: 1.5
                    mood-influence: 0.3
                    stale-state-timeout: PT10M
                    """;
            var spec = mapper.readValue(yaml, MoodConfigSpec.class);
            assertThat(spec.decayTimeConstant()).isEqualTo(java.time.Duration.ofSeconds(10));
            assertThat(spec.maxDisplacement()).isEqualTo(1.5);
            assertThat(spec.moodInfluence()).isEqualTo(0.3);
        }

        @Test
        void mental_model_config_with_kebab_case() throws Exception {
            var yaml = """
                    belief-half-life: PT12H
                    desire-half-life: PT6H
                    intention-half-life: PT3H
                    confidence-floor: 0.2
                    projection-floor: 0.3
                    min-signals-for-inference: 5
                    inference-cooldown: PT5M
                    max-signals-in-prompt: 10
                    max-buffer-size: 100
                    eviction-timeout: PT1H
                    expected-tick-interval: PT30S
                    memory-domain: conversation
                    case-type: dialogue
                    """;
            var spec = mapper.readValue(yaml, MentalModelConfigSpec.class);
            assertThat(spec.confidenceFloor()).isEqualTo(0.2);
            assertThat(spec.projectionFloor()).isEqualTo(0.3);
            assertThat(spec.minSignalsForInference()).isEqualTo(5);
            assertThat(spec.maxSignalsInPrompt()).isEqualTo(10);
            assertThat(spec.maxBufferSize()).isEqualTo(100);
            assertThat(spec.memoryDomain()).isEqualTo("conversation");
            assertThat(spec.caseType()).isEqualTo("dialogue");
        }

        @Test
        void goal_escalation_with_kebab_case() throws Exception {
            var yaml = """
                    escalation-salience-threshold: 0.9
                    min-axis-alignment-weight: 0.5
                    cross-axis-min-weight: 0.3
                    min-cross-axis-count: 2
                    escalation-cycles: 3
                    demotion-cycles: 5
                    max-primary-drive-goals: 4
                    """;
            var spec = mapper.readValue(yaml, GoalEscalationConfigSpec.class);
            assertThat(spec.escalationSalienceThreshold()).isEqualTo(0.9);
            assertThat(spec.minAxisAlignmentWeight()).isEqualTo(0.5);
            assertThat(spec.crossAxisMinWeight()).isEqualTo(0.3);
            assertThat(spec.minCrossAxisCount()).isEqualTo(2);
            assertThat(spec.escalationCycles()).isEqualTo(3);
            assertThat(spec.demotionCycles()).isEqualTo(5);
            assertThat(spec.maxPrimaryDriveGoals()).isEqualTo(4);
        }
    }

    @Nested
    class WorldKebab {

        @Test
        void affordance_with_kebab_case() throws Exception {
            var yaml = """
                    action-type: PICK_UP
                    label: Pick up
                    required-item: gloves
                    accepts-items:
                      - key
                      - coin
                    """;
            var spec = mapper.readValue(yaml, AffordanceSpec.class);
            assertThat(spec.actionType()).isEqualTo("PICK_UP");
            assertThat(spec.requiredItem()).isEqualTo("gloves");
            assertThat(spec.acceptsItems()).containsExactly("key", "coin");
        }

        @Test
        void entity_with_kebab_case() throws Exception {
            var yaml = """
                    display-name: Wooden Chest
                    """;
            var spec = mapper.readValue(yaml, EntitySpec.class);
            assertThat(spec.displayName()).isEqualTo("Wooden Chest");
        }

        @Test
        void observation_section_with_kebab_case() throws Exception {
            var yaml = """
                    type: entity-group
                    header: Objects
                    empty-message: Nothing here.
                    entity-refs:
                      - chest-01
                    required-tags:
                      - perception
                    interpretive-frame: exploration
                    """;
            var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
            var group = (ObservationSectionSpec.EntityGroupSpec) spec;
            assertThat(group.emptyMessage()).isEqualTo("Nothing here.");
            assertThat(group.entityRefs()).containsExactly("chest-01");
            assertThat(group.requiredTags()).containsExactly("perception");
            assertThat(group.interpretiveFrame()).isEqualTo("exploration");
        }

        @Test
        void renderer_with_kebab_case() throws Exception {
            var yaml = """
                    verbatim-threshold: 10
                    grouped-threshold: 50
                    """;
            var spec = mapper.readValue(yaml, RendererSpec.class);
            assertThat(spec.verbatimThreshold()).isEqualTo(10);
            assertThat(spec.groupedThreshold()).isEqualTo(50);
        }
    }

    @Nested
    class SecondarySpecsKebab {

        @Test
        void activation_max_iterations_with_kebab_case() throws Exception {
            var yaml = """
                    type: max-iterations
                    max-iterations: 10
                    """;
            var spec = mapper.readValue(yaml, ActivationSpec.class);
            var guard = (ActivationSpec.MaxIterationsGuard) spec;
            assertThat(guard.maxIterations()).isEqualTo(10);
        }

        @Test
        void agent_ref_channel_with_kebab_case() throws Exception {
            var yaml = """
                    type: channel
                    name: chat-agent
                    channel-id: ch-001
                    """;
            var spec = mapper.readValue(yaml, AgentRefSpec.class);
            var channel = (AgentRefSpec.Channel) spec;
            assertThat(channel.channelId()).isEqualTo("ch-001");
        }

        @Test
        void decomposition_llm_with_kebab_case() throws Exception {
            var yaml = """
                    type: llm
                    max-depth: 3
                    """;
            var spec = mapper.readValue(yaml, DecompositionSpec.class);
            var llm = (DecompositionSpec.Llm) spec;
            assertThat(llm.maxDepth()).isEqualTo(3);
        }

        @Test
        void convergence_policy_with_kebab_case() throws Exception {
            var yaml = """
                    type: structural
                    similarity-threshold: 0.9
                    stale-rounds: 3
                    """;
            var spec = mapper.readValue(yaml, ConvergencePolicySpec.class);
            var structural = (ConvergencePolicySpec.Structural) spec;
            assertThat(structural.similarityThreshold()).isEqualTo(0.9);
            assertThat(structural.staleRounds()).isEqualTo(3);
        }

        @Test
        void epistemic_rule_with_kebab_case() throws Exception {
            var yaml = """
                    type: explicit-acknowledgement
                    min-participants: 3
                    """;
            var spec = mapper.readValue(yaml, EpistemicRuleSpec.class);
            var explicit = (EpistemicRuleSpec.ExplicitAcknowledgement) spec;
            assertThat(explicit.minParticipants()).isEqualTo(3);
        }

        @Test
        void experience_replay_with_kebab_case() throws Exception {
            var yaml = """
                    max-traces: 20
                    min-similarity: 0.75
                    """;
            var spec = mapper.readValue(yaml, ExperienceReplaySpec.class);
            assertThat(spec.maxTraces()).isEqualTo(20);
            assertThat(spec.minSimilarity()).isEqualTo(0.75);
        }

        @Test
        void multi_swarm_coordination_with_kebab_case() throws Exception {
            var yaml = """
                    relevance-threshold: 0.6
                    escalation-threshold: 0.9
                    convergence-threshold: 0.85
                    """;
            var spec = mapper.readValue(yaml, MultiSwarmCoordinationSpec.class);
            assertThat(spec.relevanceThreshold()).isEqualTo(0.6);
            assertThat(spec.escalationThreshold()).isEqualTo(0.9);
            assertThat(spec.convergenceThreshold()).isEqualTo(0.85);
        }

        @Test
        void method_spec_with_kebab_case() throws Exception {
            var yaml = """
                    name: analyse
                    guard: "priority > 5"
                    estimated-cost: 100.0
                    estimated-duration: PT30S
                    """;
            var spec = mapper.readValue(yaml, MethodSpec.class);
            assertThat(spec.estimatedCost()).isEqualTo(100.0);
        }

        @Test
        void speech_config_with_kebab_case() throws Exception {
            var yaml = """
                    model-dir: /models/sherpa
                    num-threads: 4
                    provider: cpu
                    punctuation-model-dir: /models/punct
                    """;
            var spec = mapper.readValue(yaml, SherpaConfigSpec.class);
            assertThat(spec.modelDir()).isEqualTo("/models/sherpa");
            assertThat(spec.numThreads()).isEqualTo(4);
            assertThat(spec.punctuationModelDir()).isEqualTo("/models/punct");
        }

        @Test
        void prompt_signature_with_kebab_case() throws Exception {
            var yaml = """
                    id: routing-prompt
                    description: LLM routing prompt
                    base-system-prompt: You are a router.
                    input-type: RoutingRequest
                    output-type: RoutingDecision
                    """;
            var spec = mapper.readValue(yaml, PromptSignatureSpec.class);
            assertThat(spec.baseSystemPrompt()).isEqualTo("You are a router.");
            assertThat(spec.inputType()).isEqualTo("RoutingRequest");
            assertThat(spec.outputType()).isEqualTo("RoutingDecision");
        }
    }
}

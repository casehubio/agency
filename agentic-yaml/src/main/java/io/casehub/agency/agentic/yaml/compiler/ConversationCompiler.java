package io.casehub.agency.agentic.yaml.compiler;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.AgentResult;
import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.agentic.yaml.registry.ConvergencePolicyRegistry;
import io.casehub.agency.agentic.yaml.registry.EpistemicRuleRegistry;
import io.casehub.agency.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.agency.agentic.yaml.registry.TurnPolicyRegistry;
import io.casehub.agency.agentic.yaml.spec.AgentParticipantSpec;
import io.casehub.agency.agentic.yaml.spec.AgentRefSpec;
import io.casehub.agency.agentic.yaml.spec.ChannelExecutionStrategySpec;
import io.casehub.agency.agentic.yaml.spec.ConvergencePolicySpec;
import io.casehub.agency.agentic.yaml.spec.EpistemicRuleSpec;
import io.casehub.agency.agentic.yaml.spec.TerminationSpec;
import io.casehub.agency.agentic.yaml.spec.TurnPolicySpec;
import io.casehub.agency.conversation.ConversationState;
import io.casehub.agency.conversation.ConvergencePolicy;
import io.casehub.agency.conversation.EpistemicRule;
import io.casehub.agency.conversation.orchestration.AgentParticipant;
import io.casehub.agency.conversation.orchestration.RoundRobinTurnPolicy;
import io.casehub.agency.conversation.orchestration.TurnPolicy;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConversationCompiler {

    private final TurnPolicyRegistry turnPolicyRegistry;
    private final TerminationConditionRegistry terminationRegistry;
    private final EpistemicRuleRegistry epistemicRuleRegistry;
    private final ConvergencePolicyRegistry convergencePolicyRegistry;
    private final @Nullable ExpressionEngine expressionEngine;

    public ConversationCompiler(
            TurnPolicyRegistry turnPolicyRegistry,
            TerminationConditionRegistry terminationRegistry,
            EpistemicRuleRegistry epistemicRuleRegistry,
            ConvergencePolicyRegistry convergencePolicyRegistry,
            @Nullable ExpressionEngine expressionEngine) {
        this.turnPolicyRegistry = turnPolicyRegistry;
        this.terminationRegistry = terminationRegistry;
        this.epistemicRuleRegistry = epistemicRuleRegistry;
        this.convergencePolicyRegistry = convergencePolicyRegistry;
        this.expressionEngine = expressionEngine;
    }

    public CompiledConversation compile(
            ChannelExecutionStrategySpec.ConversationStrategySpec spec) {
        return new CompiledConversation(
                compileTurnPolicy(spec.turnPolicy()),
                compileTermination(spec.termination()),
                compileParticipants(spec.participants()),
                compileEpistemicRule(spec.epistemicRule()),
                compileConvergencePolicy(spec.convergencePolicy()));
    }

    private TurnPolicy compileTurnPolicy(@Nullable TurnPolicySpec spec) {
        if (spec == null) return new RoundRobinTurnPolicy();
        return turnPolicyRegistry.resolve(spec);
    }

    @SuppressWarnings("unchecked")
    private @Nullable TerminationCondition<ConversationState> compileTermination(
            @Nullable List<TerminationSpec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        if (specs.size() == 1) {
            return terminationRegistry.resolve(specs.get(0), expressionEngine);
        }
        @SuppressWarnings("rawtypes")
        TerminationCondition composite = terminationRegistry.resolve(specs.get(0), expressionEngine);
        for (int i = 1; i < specs.size(); i++) {
            composite = composite.or(terminationRegistry.resolve(specs.get(i), expressionEngine));
        }
        return composite;
    }

    private List<AgentParticipant> compileParticipants(List<AgentParticipantSpec> specs) {
        return specs.stream()
                .map(this::compileParticipant)
                .toList();
    }

    private AgentParticipant compileParticipant(AgentParticipantSpec spec) {
        AgentRef ref = buildAgentRef(spec.agent());
        return new AgentParticipant(ref, spec.role(), spec.systemPrompt());
    }

    @SuppressWarnings("unchecked")
    private AgentRef buildAgentRef(AgentRefSpec spec) {
        return AgentRef.external(spec.name(),
                (Object ctx) -> CompletableFuture.completedFuture(
                        AgentResult.success(null, "YAML-declared agent placeholder")));
    }

    private @Nullable EpistemicRule compileEpistemicRule(@Nullable EpistemicRuleSpec spec) {
        if (spec == null) return null;
        return epistemicRuleRegistry.resolve(spec);
    }

    private @Nullable ConvergencePolicy compileConvergencePolicy(@Nullable ConvergencePolicySpec spec) {
        if (spec == null) return null;
        return convergencePolicyRegistry.resolve(spec);
    }
}

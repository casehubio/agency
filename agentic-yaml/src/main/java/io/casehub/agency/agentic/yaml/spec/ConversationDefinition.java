package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record ConversationDefinition(
        @JsonAlias("turn-policy") @Nullable TurnPolicySpec turnPolicy,
        @JsonAlias("epistemic-rule") @Nullable EpistemicRuleSpec epistemicRule,
        @JsonAlias("convergence-policy") @Nullable ConvergencePolicySpec convergencePolicy
) {}

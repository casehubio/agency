package io.casehub.agency.core.yaml.spec;

import org.jspecify.annotations.Nullable;

public record ConversationSpec(
        @Nullable TurnPolicySpec turnPolicy,
        @Nullable EpistemicRuleSpec epistemicRule,
        @Nullable ConvergencePolicySpec convergencePolicy
) {}

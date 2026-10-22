package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.agency.deliberation.conversation.ConvergencePolicy;
import io.casehub.agency.deliberation.conversation.EpistemicRule;
import io.casehub.agency.deliberation.conversation.orchestration.AgentParticipant;
import io.casehub.agency.deliberation.conversation.orchestration.TurnPolicy;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record CompiledConversation(
        TurnPolicy turnPolicy,
        @Nullable TerminationCondition<ConversationState> termination,
        List<AgentParticipant> participants,
        @Nullable EpistemicRule epistemicRule,
        @Nullable ConvergencePolicy convergencePolicy) {}

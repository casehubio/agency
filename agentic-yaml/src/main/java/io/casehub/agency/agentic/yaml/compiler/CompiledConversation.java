package io.casehub.agency.agentic.yaml.compiler;

import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.conversation.ConversationState;
import io.casehub.agency.conversation.ConvergencePolicy;
import io.casehub.agency.conversation.EpistemicRule;
import io.casehub.agency.conversation.orchestration.AgentParticipant;
import io.casehub.agency.conversation.orchestration.TurnPolicy;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record CompiledConversation(
        TurnPolicy turnPolicy,
        @Nullable TerminationCondition<ConversationState> termination,
        List<AgentParticipant> participants,
        @Nullable EpistemicRule epistemicRule,
        @Nullable ConvergencePolicy convergencePolicy) {}

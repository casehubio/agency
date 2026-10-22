package io.casehub.agency.deliberation.conversation;

@FunctionalInterface
public interface ConvergencePolicy {
    ConvergenceSignal evaluate(ConversationState state,
                               CommonGroundState commonGround,
                               ConvergenceContext context);
}

package io.casehub.agency.conversation;

@FunctionalInterface
public interface ConvergencePolicy {
    ConvergenceSignal evaluate(ConversationState state,
                               CommonGroundState commonGround,
                               ConvergenceContext context);
}

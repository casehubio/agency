package io.casehub.agency.orchestration.termination;

import java.util.function.Predicate;

public final class Termination {
    private Termination() {}

    public static <T> GoalReached<T> goalReached(Predicate<T> predicate) {
        return new GoalReached<>(predicate);
    }

    public static <T> MaxIterationsTermination<T> maxIterations(int max) {
        return new MaxIterationsTermination<>(max);
    }
}

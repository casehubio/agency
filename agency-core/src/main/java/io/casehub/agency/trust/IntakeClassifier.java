package io.casehub.agency.trust;

@FunctionalInterface
public interface IntakeClassifier<S> {
    IntakeResult classify(S subject, IntakeContext context);
}

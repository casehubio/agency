package io.casehub.agency.orchestration.multiswarm;

public class WeightedEscalationGate implements EscalationGate {

    private final double threshold;
    private double wConflict;
    private double wContention;
    private double wDependency;
    private final double learningRate;
    private long observationCount;

    public WeightedEscalationGate(double threshold) {
        this(threshold, 0.50, 0.25, 0.25, 0.05);
    }

    public WeightedEscalationGate(double threshold, double wConflict, double wContention,
                                   double wDependency, double learningRate) {
        this.threshold = threshold;
        this.wConflict = wConflict;
        this.wContention = wContention;
        this.wDependency = wDependency;
        this.learningRate = learningRate;
    }

    @Override
    public double escalationProbability(double conflictLevel, double resourceContention, double dependencyStrength) {
        double raw = wConflict * conflictLevel + wContention * resourceContention + wDependency * dependencyStrength;
        return Math.max(0.0, Math.min(1.0, raw));
    }

    @Override
    public double threshold() {
        return threshold;
    }

    @Override
    public void learnEscalated(double conflictLevel, double resourceContention, double dependencyStrength) {
        observationCount++;
        double predicted = escalationProbability(conflictLevel, resourceContention, dependencyStrength);
        double error = 1.0 - predicted;
        adjustWeights(conflictLevel, resourceContention, dependencyStrength, error);
    }

    @Override
    public void learnNotEscalated(double conflictLevel, double resourceContention, double dependencyStrength) {
        observationCount++;
        double predicted = escalationProbability(conflictLevel, resourceContention, dependencyStrength);
        double error = 0.0 - predicted;
        adjustWeights(conflictLevel, resourceContention, dependencyStrength, error);
    }

    private void adjustWeights(double conflict, double contention, double dependency, double error) {
        double lr = learningRate / (1.0 + observationCount * 0.01);
        wConflict = clampWeight(wConflict + lr * error * conflict);
        wContention = clampWeight(wContention + lr * error * contention);
        wDependency = clampWeight(wDependency + lr * error * dependency);
    }

    private static double clampWeight(double w) {
        return Math.max(0.01, Math.min(1.0, w));
    }
}

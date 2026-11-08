package io.casehub.agency.orchestration.multiswarm;

public class WeightedRelevanceScorer implements RelevanceScorer {

    private final double threshold;
    private double wOverlap;
    private double wDomain;
    private double wStrength;
    private double wHistory;
    private final double learningRate;
    private long observationCount;

    public WeightedRelevanceScorer(double threshold) {
        this(threshold, 0.30, 0.30, 0.25, 0.15, 0.05);
    }

    public WeightedRelevanceScorer(double threshold, double wOverlap, double wDomain,
                                    double wStrength, double wHistory, double learningRate) {
        this.threshold = threshold;
        this.wOverlap = wOverlap;
        this.wDomain = wDomain;
        this.wStrength = wStrength;
        this.wHistory = wHistory;
        this.learningRate = learningRate;
    }

    @Override
    public double score(double capabilityOverlap, double domainMatch, double signalStrength, double historicalRelevance) {
        double raw = wOverlap * capabilityOverlap + wDomain * domainMatch
                + wStrength * signalStrength + wHistory * historicalRelevance;
        return Math.max(0.0, Math.min(1.0, raw));
    }

    @Override
    public double threshold() {
        return threshold;
    }

    @Override
    public void learnRelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        observationCount++;
        double predicted = score(capabilityOverlap, domainMatch, signalStrength, 0.5);
        double error = 1.0 - predicted;
        adjustWeights(capabilityOverlap, domainMatch, signalStrength, error);
    }

    @Override
    public void learnIrrelevant(double capabilityOverlap, double domainMatch, double signalStrength) {
        observationCount++;
        double predicted = score(capabilityOverlap, domainMatch, signalStrength, 0.5);
        double error = 0.0 - predicted;
        adjustWeights(capabilityOverlap, domainMatch, signalStrength, error);
    }

    private void adjustWeights(double overlap, double domain, double strength, double error) {
        double lr = learningRate / (1.0 + observationCount * 0.01);
        wOverlap = clampWeight(wOverlap + lr * error * overlap);
        wDomain = clampWeight(wDomain + lr * error * domain);
        wStrength = clampWeight(wStrength + lr * error * strength);
    }

    private static double clampWeight(double w) {
        return Math.max(0.01, Math.min(1.0, w));
    }
}

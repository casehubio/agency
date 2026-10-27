package io.casehub.agency.social.memory.evolution;

public record MemoryHygieneOutcome(
    double retentionQuality,
    double evictionRate,
    double consolidationRate,
    int lowRetentionCount,
    int integrityViolations,
    int reflectionsGenerated,
    int sampleSize) {}

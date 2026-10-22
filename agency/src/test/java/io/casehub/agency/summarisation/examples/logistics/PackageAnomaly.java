package io.casehub.agency.summarisation.examples.logistics;

public record PackageAnomaly(AnomalyType type, String severity, String details, String rationale) {}

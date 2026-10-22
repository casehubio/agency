package io.casehub.agency.summarisation.examples.clinical;

public record ClinicalEvent(ClinicalCategory category, Severity severity, String description, String rationale) {}

package io.casehub.agency.orchestration.degradation;

public record DegradedEvent(String capability, String reason, boolean fallbackActive) {
}

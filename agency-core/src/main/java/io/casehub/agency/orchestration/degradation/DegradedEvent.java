package io.casehub.agency.orchestration.degradation;

public record DegradedEvent(String capability, String reason, boolean fallbackActive, boolean collapsed) {
    public DegradedEvent(String capability, String reason, boolean fallbackActive) {
        this(capability, reason, fallbackActive, false);
    }
}

package io.casehub.agency.orchestration.swarm;

public record SwarmDegradedEvent(String capability, String reason, boolean fallbackActive) {
}

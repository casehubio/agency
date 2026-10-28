package io.casehub.agency.deliberation.protocol;

import io.casehub.platform.coordination.protocol.AdvisorySeverity;
import io.casehub.platform.coordination.protocol.ProtocolAdvisory;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

import java.util.List;
import java.util.Map;

public final class AdvisoryMapper {

    private AdvisoryMapper() {}

    public static DispatchAdvisory toDispatchAdvisory(ProtocolAdvisory advisory, String source) {
        return new DispatchAdvisory(
                source,
                mapSeverity(advisory.severity()),
                advisory.message(),
                Map.of(),
                mapAction(advisory.suggestedAction()));
    }

    public static List<DispatchAdvisory> toDispatchAdvisories(List<ProtocolAdvisory> advisories,
                                                              String source) {
        return advisories.stream()
                .map(a -> toDispatchAdvisory(a, source))
                .toList();
    }

    private static Severity mapSeverity(AdvisorySeverity severity) {
        return switch (severity) {
            case INFO -> Severity.ADVISORY;
            case WARNING -> Severity.WARNING;
            case CRITICAL -> Severity.CRITICAL;
        };
    }

    private static SuggestedAction mapAction(
            io.casehub.platform.coordination.protocol.SuggestedAction action) {
        return switch (action) {
            case LOG -> SuggestedAction.LOG;
            case ESCALATE -> SuggestedAction.ESCALATE;
            case INVESTIGATE -> SuggestedAction.INVESTIGATE;
            case REROUTE -> SuggestedAction.REROUTE;
        };
    }
}

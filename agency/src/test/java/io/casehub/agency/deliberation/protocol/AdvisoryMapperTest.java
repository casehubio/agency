package io.casehub.agency.deliberation.protocol;

import io.casehub.platform.coordination.protocol.AdvisorySeverity;
import io.casehub.platform.coordination.protocol.ProtocolAdvisory;
import io.casehub.platform.coordination.protocol.SuggestedAction;
import io.casehub.qhorus.api.spi.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AdvisoryMapperTest {

    static Stream<Arguments> severityMappings() {
        return Stream.of(
                Arguments.of(AdvisorySeverity.INFO, Severity.ADVISORY),
                Arguments.of(AdvisorySeverity.WARNING, Severity.WARNING),
                Arguments.of(AdvisorySeverity.CRITICAL, Severity.CRITICAL));
    }

    @ParameterizedTest
    @MethodSource("severityMappings")
    void maps_severity_correctly(AdvisorySeverity input, Severity expected) {
        var advisory = new ProtocolAdvisory(input, "test message", SuggestedAction.LOG);
        var result = AdvisoryMapper.toDispatchAdvisory(advisory, "TEST");
        assertThat(result.severity()).isEqualTo(expected);
    }

    static Stream<Arguments> actionMappings() {
        return Stream.of(
                Arguments.of(SuggestedAction.LOG, io.casehub.qhorus.api.spi.SuggestedAction.LOG),
                Arguments.of(SuggestedAction.ESCALATE, io.casehub.qhorus.api.spi.SuggestedAction.ESCALATE),
                Arguments.of(SuggestedAction.INVESTIGATE, io.casehub.qhorus.api.spi.SuggestedAction.INVESTIGATE),
                Arguments.of(SuggestedAction.REROUTE, io.casehub.qhorus.api.spi.SuggestedAction.REROUTE));
    }

    @ParameterizedTest
    @MethodSource("actionMappings")
    void maps_action_correctly(SuggestedAction input,
                               io.casehub.qhorus.api.spi.SuggestedAction expected) {
        var advisory = new ProtocolAdvisory(AdvisorySeverity.WARNING, "msg", input);
        var result = AdvisoryMapper.toDispatchAdvisory(advisory, "TEST");
        assertThat(result.suggestedAction()).isEqualTo(expected);
    }

    @Test
    void preserves_source_and_message() {
        var advisory = new ProtocolAdvisory(AdvisorySeverity.WARNING, "test message",
                SuggestedAction.INVESTIGATE);
        var result = AdvisoryMapper.toDispatchAdvisory(advisory, "NEGOTIATION");
        assertThat(result.source()).isEqualTo("NEGOTIATION");
        assertThat(result.message()).isEqualTo("test message");
    }

    @Test
    void evidence_is_always_empty() {
        var advisory = new ProtocolAdvisory(AdvisorySeverity.INFO, "msg", SuggestedAction.LOG);
        var result = AdvisoryMapper.toDispatchAdvisory(advisory, "TEST");
        assertThat(result.evidence()).isEqualTo(Map.of());
    }

    @Test
    void toDispatchAdvisories_maps_list() {
        var advisories = List.of(
                new ProtocolAdvisory(AdvisorySeverity.INFO, "msg1", SuggestedAction.LOG),
                new ProtocolAdvisory(AdvisorySeverity.CRITICAL, "msg2", SuggestedAction.ESCALATE));
        var results = AdvisoryMapper.toDispatchAdvisories(advisories, "SRC");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).severity()).isEqualTo(Severity.ADVISORY);
        assertThat(results.get(1).severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void toDispatchAdvisories_empty_list_returns_empty() {
        var results = AdvisoryMapper.toDispatchAdvisories(List.of(), "SRC");
        assertThat(results).isEmpty();
    }
}

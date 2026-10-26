package io.casehub.agency.social.cognition.evolution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.api.model.stigmergy.TickTrace;
import io.casehub.api.model.stigmergy.TickTrace.GateResult;
import io.casehub.api.model.stigmergy.TickTrace.GateResult.GateVerdict;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CognitiveEvolutionAdapterTest {

    private final EvolutionTickBridge bridge = mock(EvolutionTickBridge.class);
    private final DriveOrchestrator drives = mock(DriveOrchestrator.class);
    private final MoodOrchestrator mood = mock(MoodOrchestrator.class);

    private static final TickTrace CLEAN_TRACE = new TickTrace(UUID.randomUUID(), Instant.now(),
            TickTrace.TickTrigger.EVENT_DRIVEN, List.of(
                    new GateResult("circuit_breaker_check", GateVerdict.PASSED, null)), null);

    private DriveProfile profileWithCuriosity(double curiosity) {
        return new DriveProfile("agent-1", "tenant-1",
                Map.of(DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, curiosity, "test"),
                        DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "test"),
                        DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "test"),
                        DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "test")),
                0.5, DriveAxis.CURIOSITY, Instant.now());
    }

    private CognitiveEvolutionAdapter adapter(CognitiveEvolutionConfig config) {
        return new CognitiveEvolutionAdapter(bridge, drives, mood, config);
    }

    @Test
    void ticks_when_curiosity_above_full_engage() {
        when(drives.currentDrives("agent-1", "tenant-1"))
                .thenReturn(Optional.of(profileWithCuriosity(0.9)));
        when(bridge.tick()).thenReturn(CLEAN_TRACE);

        adapter(CognitiveEvolutionConfig.defaults()).tickIfDue("agent-1", "tenant-1");
        verify(bridge).tick();
    }

    @Test
    void skips_when_curiosity_below_floor() {
        when(drives.currentDrives("agent-1", "tenant-1"))
                .thenReturn(Optional.of(profileWithCuriosity(0.1)));

        adapter(CognitiveEvolutionConfig.defaults()).tickIfDue("agent-1", "tenant-1");
        verify(bridge, never()).tick();
    }

    @Test
    void ticks_unconditionally_when_no_drive_data() {
        when(drives.currentDrives("agent-1", "tenant-1"))
                .thenReturn(Optional.empty());
        when(bridge.tick()).thenReturn(CLEAN_TRACE);

        adapter(CognitiveEvolutionConfig.defaults()).tickIfDue("agent-1", "tenant-1");
        verify(bridge).tick();
    }

    @Test
    void always_ticks_with_always_tick_config() {
        when(drives.currentDrives("agent-1", "tenant-1"))
                .thenReturn(Optional.of(profileWithCuriosity(0.0)));
        when(bridge.tick()).thenReturn(CLEAN_TRACE);

        adapter(CognitiveEvolutionConfig.alwaysTick()).tickIfDue("agent-1", "tenant-1");
        verify(bridge).tick();
    }

    @Test
    void feeds_mood_signal_on_circuit_breaker_blocked() {
        when(drives.currentDrives("agent-1", "tenant-1"))
                .thenReturn(Optional.of(profileWithCuriosity(0.9)));
        var blockedTrace = new TickTrace(UUID.randomUUID(), Instant.now(),
                TickTrace.TickTrigger.EVENT_DRIVEN, List.of(
                        new GateResult("circuit_breaker_check", GateVerdict.BLOCKED, "open")), null);
        when(bridge.tick()).thenReturn(blockedTrace);

        adapter(CognitiveEvolutionConfig.defaults()).tickIfDue("agent-1", "tenant-1");
        verify(mood).record(any(MoodSignal.DirectShift.class), eq("agent-1"), eq("tenant-1"));
    }

    @Test
    void no_mood_signal_when_disabled() {
        when(drives.currentDrives("agent-1", "tenant-1"))
                .thenReturn(Optional.of(profileWithCuriosity(0.9)));
        var blockedTrace = new TickTrace(UUID.randomUUID(), Instant.now(),
                TickTrace.TickTrigger.EVENT_DRIVEN, List.of(
                        new GateResult("circuit_breaker_check", GateVerdict.BLOCKED, "open")), null);
        when(bridge.tick()).thenReturn(blockedTrace);

        adapter(new CognitiveEvolutionConfig(0.3, 0.7, false))
                .tickIfDue("agent-1", "tenant-1");
        verify(mood, never()).record(any(), any(), any());
    }

    @Test
    void works_without_mood_orchestrator() {
        when(drives.currentDrives("agent-1", "tenant-1"))
                .thenReturn(Optional.of(profileWithCuriosity(0.9)));
        when(bridge.tick()).thenReturn(CLEAN_TRACE);

        var adapter = new CognitiveEvolutionAdapter(bridge, drives, null,
                CognitiveEvolutionConfig.defaults());
        adapter.tickIfDue("agent-1", "tenant-1");
        verify(bridge).tick();
    }
}

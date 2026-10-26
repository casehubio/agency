package io.casehub.agency.social.cognition.evolution;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

public class CognitiveEvolutionAdapter {

    private static final System.Logger LOG = System.getLogger(CognitiveEvolutionAdapter.class.getName());

    private final EvolutionTickBridge bridge;
    private final DriveOrchestrator drives;
    private final @Nullable MoodOrchestrator mood;
    private final CognitiveEvolutionConfig config;
    private final CircuitBreakerMoodMapper cbMapper = new CircuitBreakerMoodMapper();

    public CognitiveEvolutionAdapter(EvolutionTickBridge bridge,
                                     DriveOrchestrator drives,
                                     @Nullable MoodOrchestrator mood,
                                     CognitiveEvolutionConfig config) {
        this.bridge = Objects.requireNonNull(bridge);
        this.drives = Objects.requireNonNull(drives);
        this.mood = mood;
        this.config = Objects.requireNonNull(config);
    }

    public void tickIfDue(String agentId, String tenantId) {
        var profile = drives.currentDrives(agentId, tenantId).orElse(null);
        if (!shouldTick(profile)) return;

        try {
            var trace = bridge.tick();
            if (config.circuitBreakerMoodEnabled() && mood != null) {
                for (var signal : cbMapper.map(trace)) {
                    mood.record(signal, agentId, tenantId);
                }
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Evolution tick failed for agent " + agentId, e);
        }
    }

    private boolean shouldTick(@Nullable DriveProfile profile) {
        if (profile == null) return true;
        var curiosity = profile.drives().get(DriveAxis.CURIOSITY);
        if (curiosity == null) return true;
        double intensity = curiosity.intensity();
        if (intensity < config.curiosityFloor()) return false;
        if (intensity >= config.curiosityFullEngage()) return true;
        double probability = (intensity - config.curiosityFloor())
                / (config.curiosityFullEngage() - config.curiosityFloor());
        return ThreadLocalRandom.current().nextDouble() < probability;
    }
}

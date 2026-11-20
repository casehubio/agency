package io.casehub.agency.speech.demo;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ModelProvisioningService {

    public enum ModelStatus {PENDING, DOWNLOADING, READY, ERROR}

    static final Map<String, String> KOKORO_MODELS = new LinkedHashMap<>(Map.of(
            "kokoro", "kokoro-multi-lang-v1_0"));

    private static final System.Logger LOG = System.getLogger("speech-demo.provisioning");

    private final ConcurrentHashMap<String, ModelStatus> statuses = new ConcurrentHashMap<>();

    void onStartup(@Observes StartupEvent ev) {
        init();
        Thread.ofVirtual().name("model-provisioner").start(this::provisionAll);
    }

    void init() {
        KOKORO_MODELS.keySet().forEach(key -> statuses.put(key, ModelStatus.PENDING));
        statuses.put("streaming-stt", ModelStatus.PENDING);
    }

    void provisionAll() {
        for (var entry : KOKORO_MODELS.entrySet()) {
            provisionEntry(entry.getKey(), () -> provisionKokoro(entry.getValue()));
        }
        provisionEntry("streaming-stt", this::provisionStreamingStt);
    }

    private void provisionEntry(String key, Runnable provisioner) {
        statuses.put(key, ModelStatus.DOWNLOADING);
        try {
            LOG.log(System.Logger.Level.INFO, "Provisioning model: {0}", key);
            provisioner.run();
            statuses.put(key, ModelStatus.READY);
            LOG.log(System.Logger.Level.INFO, "Model ready: {0}", key);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to provision " + key + ": " + e.getMessage());
            statuses.put(key, ModelStatus.ERROR);
        }
    }

    void provisionKokoro(String modelName) {
        io.casehub.connectors.voice.sherpa.KokoroTextToSpeech.ensureProvisioned();
    }

    void provisionStreamingStt() {
        io.casehub.connectors.voice.sherpa.SherpaOnnxStreamingSpeechToText.ensureProvisioned();
    }


    public Map<String, ModelStatus> status() {
        return Map.copyOf(statuses);
    }

    public boolean allReady() {
        return !statuses.isEmpty() && statuses.values().stream().allMatch(s -> s == ModelStatus.READY);
    }
}

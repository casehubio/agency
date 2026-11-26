package io.casehub.agency.speech.demo;

import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ModelProvisioningService {

    public enum ModelStatus {PENDING, DOWNLOADING, READY, ERROR}

    private static final System.Logger LOG = System.getLogger("speech-demo.provisioning");

    @Inject
    Instance<TtsModelRegistry> ttsRegistry;

    @Inject
    Instance<StreamingSpeechToText> sttInstance;

    private final ConcurrentHashMap<String, ModelStatus> statuses = new ConcurrentHashMap<>();

    void onStartup(@Observes StartupEvent ev) {
        init();
    }

    void init() {
        if (ttsRegistry.isResolvable()) {
            for (String engineKey : engineKeys()) {
                statuses.put(engineKey, ModelStatus.READY);
            }
        }
        if (sttInstance.isResolvable()) {
            statuses.put("streaming-stt", ModelStatus.READY);
        }
    }

    private Set<String> engineKeys() {
        var keys = new TreeSet<String>();
        for (String modelKey : ttsRegistry.get().models().keySet()) {
            int colon = modelKey.indexOf(':');
            keys.add(colon > 0 ? modelKey.substring(0, colon) : modelKey);
        }
        return keys;
    }

    public Map<String, ModelStatus> status() {
        return Map.copyOf(statuses);
    }

    public boolean allReady() {
        return !statuses.isEmpty() && statuses.values().stream().allMatch(s -> s == ModelStatus.READY);
    }
}

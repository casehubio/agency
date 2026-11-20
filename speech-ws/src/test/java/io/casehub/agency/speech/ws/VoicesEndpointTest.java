package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.TextToSpeech;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoicesEndpointTest {

    private static final TextToSpeech STUB = mock(TextToSpeech.class);

    @SuppressWarnings("unchecked")
    private static Instance<TtsModelRegistry> instanceOf(TtsModelRegistry registry) {
        Instance<TtsModelRegistry> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(registry);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static Instance<TtsModelRegistry> emptyInstance() {
        Instance<TtsModelRegistry> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(false);
        return instance;
    }

    @Test
    void returnsVoiceKeysSorted() {
        var models = new LinkedHashMap<String, TextToSpeech>();
        models.put("kokoro:af_heart", STUB);
        models.put("amy", STUB);
        models.put("qwen3:ryan", STUB);
        models.put("lessac-medium", STUB);

        var registry = new TtsModelRegistry(models);
        var endpoint = new VoicesEndpoint();
        endpoint.ttsRegistry = instanceOf(registry);

        var response = endpoint.voices();
        assertThat(response.getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        var body = (List<String>) response.getEntity();
        assertThat(body).containsExactly("amy", "kokoro:af_heart", "lessac-medium", "qwen3:ryan");
    }

    @Test
    void returnsEmptyListWhenNoModels() {
        var registry = new TtsModelRegistry();
        var endpoint = new VoicesEndpoint();
        endpoint.ttsRegistry = instanceOf(registry);

        var response = endpoint.voices();
        assertThat(response.getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        var body = (List<String>) response.getEntity();
        assertThat(body).isEmpty();
    }

    @Test
    void returnsEmptyListWhenRegistryUnresolvable() {
        var endpoint = new VoicesEndpoint();
        endpoint.ttsRegistry = emptyInstance();

        var response = endpoint.voices();
        assertThat(response.getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        var body = (List<String>) response.getEntity();
        assertThat(body).isEmpty();
    }
}

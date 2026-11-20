package io.casehub.agency.speech.demo;

import io.casehub.agency.speech.ws.TtsModelRegistry;
import io.casehub.connectors.voice.spi.TextToSpeech;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VoicesEndpointTest {

    private static final TextToSpeech STUB = mock(TextToSpeech.class);

    @Test
    void returnsVoiceKeysSorted() {
        var models = new LinkedHashMap<String, TextToSpeech>();
        models.put("kokoro:af_heart", STUB);
        models.put("amy", STUB);
        models.put("qwen3:ryan", STUB);
        models.put("lessac-medium", STUB);

        var registry = new TtsModelRegistry(models);
        var endpoint = new VoicesEndpoint();
        endpoint.ttsRegistry = registry;

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
        endpoint.ttsRegistry = registry;

        var response = endpoint.voices();
        assertThat(response.getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        var body = (List<String>) response.getEntity();
        assertThat(body).isEmpty();
    }
}

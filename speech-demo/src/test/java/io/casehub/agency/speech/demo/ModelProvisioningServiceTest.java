package io.casehub.agency.speech.demo;

import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.agency.speech.demo.ModelProvisioningService.ModelStatus;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelProvisioningServiceTest {

    @SuppressWarnings("unchecked")
    private ModelProvisioningService createService(TtsModelRegistry registry, boolean hasStt) {
        var service = new ModelProvisioningService();
        var ttsInstance = (Instance<TtsModelRegistry>) mock(Instance.class);
        var sttInstance = (Instance<StreamingSpeechToText>) mock(Instance.class);
        when(ttsInstance.isResolvable()).thenReturn(registry != null);
        if (registry != null) when(ttsInstance.get()).thenReturn(registry);
        when(sttInstance.isResolvable()).thenReturn(hasStt);
        try {
            var ttsField = ModelProvisioningService.class.getDeclaredField("ttsRegistry");
            ttsField.setAccessible(true);
            ttsField.set(service, ttsInstance);
            var sttField = ModelProvisioningService.class.getDeclaredField("sttInstance");
            sttField.setAccessible(true);
            sttField.set(service, sttInstance);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return service;
    }

    @Test
    void init_extractsEngineKeysFromRegistry() {
        var tts = mock(TextToSpeech.class);
        var registry = new TtsModelRegistry(Map.of(
                "kokoro:af_heart", tts,
                "kokoro:am_adam", tts,
                "sherpa:default", tts));
        var service = createService(registry, true);

        service.init();

        assertThat(service.status()).hasSize(3);
        assertThat(service.status()).containsKeys("kokoro", "sherpa", "streaming-stt");
        assertThat(service.allReady()).isTrue();
    }

    @Test
    void init_emptyWithNoRegistry() {
        var service = createService(null, false);

        service.init();

        assertThat(service.status()).isEmpty();
        assertThat(service.allReady()).isFalse();
    }

    @Test
    void init_sttOnlyWhenNoTts() {
        var service = createService(null, true);

        service.init();

        assertThat(service.status()).hasSize(1);
        assertThat(service.status()).containsKey("streaming-stt");
        assertThat(service.allReady()).isTrue();
    }

    @Test
    void statusReturnsDefensiveCopy() {
        var tts = mock(TextToSpeech.class);
        var registry = new TtsModelRegistry(Map.of("kokoro:af_heart", tts));
        var service = createService(registry, false);

        service.init();
        var copy = service.status();

        assertThat(copy).hasSize(1);
        assertThat(copy.get("kokoro")).isEqualTo(ModelStatus.READY);
    }
}

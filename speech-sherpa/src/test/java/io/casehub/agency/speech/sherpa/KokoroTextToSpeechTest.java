package io.casehub.agency.speech.sherpa;

import io.casehub.agency.speech.SynthesisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KokoroTextToSpeechTest {

    @TempDir Path tempDir;

    @Test
    void rejectsNullLib() {
        assertThatThrownBy(() -> new KokoroTextToSpeech(KokoroConfig.defaults(tempDir), (SherpaLibrary) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("lib");
    }

    @Test
    void rejectsNullConfig() {
        assertThatThrownBy(() -> new KokoroTextToSpeech(null, (SherpaLibrary) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
    }

    @Test
    void implementsTextToSpeechService() {
        assertThat(io.casehub.agency.speech.TextToSpeechService.class)
                .isAssignableFrom(KokoroTextToSpeech.class);
    }
}

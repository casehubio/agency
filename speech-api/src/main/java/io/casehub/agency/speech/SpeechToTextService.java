package io.casehub.agency.speech;

import java.nio.file.Path;

public interface SpeechToTextService {
    TranscriptionResult transcribe(Path audioFile, TranscriptionOptions options);
}

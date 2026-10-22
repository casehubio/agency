package io.casehub.agency.speech;

public interface StreamingSpeechToTextService {
    RecognitionStream startStream(TranscriptionOptions options);
}

package io.casehub.agency.speech;

public interface TextToSpeechService {
    SynthesisResult synthesise(String text, SynthesisOptions options);

    default void warmUp() {}

}

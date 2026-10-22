package io.casehub.agency.speech;

public interface SpeakerEmbeddingExtractor {
    SpeakerEmbedding extract(float[] samples, int sampleRate);
}

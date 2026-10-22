package io.casehub.agency.speech;

public interface VoiceActivityFilter extends AutoCloseable {
    float[] filterChunk(float[] samples, int sampleRate);
    void reset();
    void close();
}

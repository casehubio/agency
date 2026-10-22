package io.casehub.agency.speech;

public interface RecognitionStream extends AutoCloseable {
    void acceptSamples(float[] samples, int sampleRate);

    boolean isEndpointDetected();

    String partialResult();

    TranscriptionResult finalResult();

    void close();
}

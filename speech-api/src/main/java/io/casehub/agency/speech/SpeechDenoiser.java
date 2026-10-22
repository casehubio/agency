package io.casehub.agency.speech;

public interface SpeechDenoiser {
    float[] denoise(float[] samples, int sampleRate);
}

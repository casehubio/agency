package io.casehub.agency.speech.sherpa;

@FunctionalInterface
interface TtsTokenizer {
    int[] encode(String text);
}

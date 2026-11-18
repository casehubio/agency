package io.casehub.agency.speech.ws.protocol;

public sealed interface Cue {
    record GestureCue(String tag, long offsetMs) implements Cue {}
    record MoodCue(String mood, long offsetMs) implements Cue {}
}

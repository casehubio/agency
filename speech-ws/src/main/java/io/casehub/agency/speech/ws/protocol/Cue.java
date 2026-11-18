package io.casehub.agency.speech.ws.protocol;

public sealed interface Cue {
    record GestureCue(String tag, long offsetMs, int charOffset) implements Cue {
        public GestureCue(String tag, long offsetMs) { this(tag, offsetMs, -1); }
    }
    record MoodCue(String mood, long offsetMs, int charOffset) implements Cue {
        public MoodCue(String mood, long offsetMs) { this(mood, offsetMs, -1); }
    }
}

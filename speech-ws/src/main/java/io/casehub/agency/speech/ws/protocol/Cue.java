package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Cue.GestureCue.class, name = "gesture"),
    @JsonSubTypes.Type(value = Cue.MoodCue.class, name = "mood")
})
public sealed interface Cue {
    record GestureCue(String tag, @JsonAlias("offset-ms") long offsetMs,
                      @JsonAlias("char-offset") int charOffset) implements Cue {
        public GestureCue(String tag, long offsetMs) { this(tag, offsetMs, -1); }
    }
    record MoodCue(String mood, @JsonAlias("offset-ms") long offsetMs,
                   @JsonAlias("char-offset") int charOffset) implements Cue {
        public MoodCue(String mood, long offsetMs) { this(mood, offsetMs, -1); }
    }
}

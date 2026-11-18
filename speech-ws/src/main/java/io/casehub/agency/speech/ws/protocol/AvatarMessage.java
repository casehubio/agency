package io.casehub.agency.speech.ws.protocol;

import java.util.List;

public sealed interface AvatarMessage {
    record Start(int sampleRate, @org.jspecify.annotations.Nullable String llmModel,
                 @org.jspecify.annotations.Nullable String ttsModel) implements AvatarMessage {
        public Start(int sampleRate) {this(sampleRate, null, null);}
    }

    record Stop() implements AvatarMessage {}

    record Text(String text, @org.jspecify.annotations.Nullable String llmModel,
                @org.jspecify.annotations.Nullable String ttsModel) implements AvatarMessage {
        public Text(String text) {this(text, null, null);}
    }

    record Partial(String text) implements AvatarMessage {}

    record Transcript(String text) implements AvatarMessage {}

    record Error(String message) implements AvatarMessage {}

    record SpeakerPrompt(String message) implements AvatarMessage {}

    record SpeakerIdentify(String name) implements AvatarMessage {}

    record SpeakerIdentified(String name, double confidence) implements AvatarMessage {}

    // Turn-framed protocol (D3)
    record Segment(int turnSeq, int index, String text, boolean hasAudio,
                   List<VisemeFrame> visemes, List<Cue> cues) implements AvatarMessage {
        public Segment {
                           visemes = List.copyOf(visemes);
                           cues    = List.copyOf(cues);
                       }
    }

    record TurnComplete(int turnSeq, int segmentCount, TurnOrigin origin) implements AvatarMessage {}

    record Gesture(String tag, GestureOrigin origin) implements AvatarMessage {}

    record Mood(String state) implements AvatarMessage {}

    record PlaybackComplete(int turnSeq) implements AvatarMessage {}

    record UserActivity(UserActivityKind kind) implements AvatarMessage {}
}

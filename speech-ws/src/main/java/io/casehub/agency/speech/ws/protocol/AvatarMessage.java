package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AvatarMessage.Start.class, name = "start"),
    @JsonSubTypes.Type(value = AvatarMessage.Stop.class, name = "stop"),
    @JsonSubTypes.Type(value = AvatarMessage.Text.class, name = "text"),
    @JsonSubTypes.Type(value = AvatarMessage.Partial.class, name = "partial"),
    @JsonSubTypes.Type(value = AvatarMessage.Transcript.class, name = "transcript"),
    @JsonSubTypes.Type(value = AvatarMessage.Error.class, name = "error"),
    @JsonSubTypes.Type(value = AvatarMessage.SpeakerPrompt.class, names = {"speakerPrompt", "speaker-prompt"}),
    @JsonSubTypes.Type(value = AvatarMessage.SpeakerIdentify.class, names = {"speakerIdentify", "speaker-identify"}),
    @JsonSubTypes.Type(value = AvatarMessage.SpeakerIdentified.class, names = {"speakerIdentified", "speaker-identified"}),
    @JsonSubTypes.Type(value = AvatarMessage.Segment.class, name = "segment"),
    @JsonSubTypes.Type(value = AvatarMessage.TurnComplete.class, names = {"turnComplete", "turn-complete"}),
    @JsonSubTypes.Type(value = AvatarMessage.Gesture.class, name = "gesture"),
    @JsonSubTypes.Type(value = AvatarMessage.Mood.class, name = "mood"),
    @JsonSubTypes.Type(value = AvatarMessage.PlaybackComplete.class, names = {"playbackComplete", "playback-complete"}),
    @JsonSubTypes.Type(value = AvatarMessage.UserActivity.class, names = {"userActivity", "user-activity"})
})
public sealed interface AvatarMessage {
    record Start(@JsonAlias("sample-rate") int sampleRate,
                 @org.jspecify.annotations.Nullable @JsonAlias("llm-model") String llmModel,
                 @org.jspecify.annotations.Nullable @JsonAlias("tts-model") String ttsModel) implements AvatarMessage {
        public Start(int sampleRate) {this(sampleRate, null, null);}

        @JsonCreator
        public static Start create(
                @JsonProperty("sampleRate") @JsonAlias("sample-rate") Integer sampleRate,
                @JsonProperty("llmModel") @JsonAlias("llm-model") String llmModel,
                @JsonProperty("ttsModel") @JsonAlias("tts-model") String ttsModel) {
            return new Start(sampleRate != null ? sampleRate : 16000, llmModel, ttsModel);
        }
    }

    record Stop() implements AvatarMessage {}

    record Text(String text,
                @org.jspecify.annotations.Nullable @JsonAlias("llm-model") String llmModel,
                @org.jspecify.annotations.Nullable @JsonAlias("tts-model") String ttsModel) implements AvatarMessage {
        public Text(String text) {this(text, null, null);}
    }

    record Partial(String text) implements AvatarMessage {}

    record Transcript(String text) implements AvatarMessage {}

    record Error(String message) implements AvatarMessage {}

    record SpeakerPrompt(String message) implements AvatarMessage {}

    record SpeakerIdentify(String name) implements AvatarMessage {}

    record SpeakerIdentified(String name, double confidence) implements AvatarMessage {}

    record Segment(@JsonAlias("turn-seq") int turnSeq,
                   int index, String text,
                   @JsonAlias("has-audio") boolean hasAudio,
                   List<VisemeFrame> visemes, List<Cue> cues) implements AvatarMessage {
        public Segment {
                           visemes = List.copyOf(visemes);
                           cues    = List.copyOf(cues);
                       }
    }

    record TurnComplete(@JsonAlias("turn-seq") int turnSeq,
                        @JsonAlias("segment-count") int segmentCount,
                        TurnOrigin origin) implements AvatarMessage {}

    record Gesture(String tag, GestureOrigin origin) implements AvatarMessage {}

    record Mood(String state) implements AvatarMessage {}

    record PlaybackComplete(@JsonAlias("turn-seq") int turnSeq) implements AvatarMessage {}

    record UserActivity(UserActivityKind kind) implements AvatarMessage {}
}

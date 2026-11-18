package io.casehub.agency.speech.ws.protocol;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    @Test
    void encodesPartialMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Partial("hello"));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("partial");
        assertThat(obj.get("text").getAsString()).isEqualTo("hello");
    }

    @Test
    void encodesTranscriptMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Transcript("Hello world."));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("transcript");
        assertThat(obj.get("text").getAsString()).isEqualTo("Hello world.");
    }

    @Test
    void encodesErrorMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Error("service down"));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("error");
        assertThat(obj.get("message").getAsString()).isEqualTo("service down");
    }

    @Test
    void encodesStartMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Start(44100));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("start");
        assertThat(obj.get("sampleRate").getAsInt()).isEqualTo(44100);
    }

    @Test
    void decodesStartMessage() {
        var msg = MessageCodec.decodeClient("{\"type\":\"start\",\"sampleRate\":16000}");
        assertThat(msg).isInstanceOf(AvatarMessage.Start.class);
        assertThat(((AvatarMessage.Start) msg).sampleRate()).isEqualTo(16000);
    }

    @Test
    void decodesStartMessageWithDefaultSampleRate() {
        var msg = MessageCodec.decodeClient("{\"type\":\"start\"}");
        assertThat(((AvatarMessage.Start) msg).sampleRate()).isEqualTo(16000);
    }

    @Test
    void decodesStopMessage() {
        var msg = MessageCodec.decodeClient("{\"type\":\"stop\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.Stop.class);
    }

    @Test
    void rejectsUnknownClientMessageType() {
        assertThatThrownBy(() -> MessageCodec.decodeClient("{\"type\":\"foo\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown client message type");
    }

    @Test
    void encodesSpeakerPrompt() {
        String json = MessageCodec.encode(new AvatarMessage.SpeakerPrompt("What's your name?"));
        var    obj  = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("speakerPrompt");
        assertThat(obj.get("message").getAsString()).isEqualTo("What's your name?");
    }

    @Test
    void encodesSpeakerIdentified() {
        String json = MessageCodec.encode(new AvatarMessage.SpeakerIdentified("Mark", 0.95));
        var    obj  = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("speakerIdentified");
        assertThat(obj.get("name").getAsString()).isEqualTo("Mark");
        assertThat(obj.get("confidence").getAsDouble()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01));
    }


    @Test
    void decodeSpeakerIdentify() {
        var msg = MessageCodec.decodeClient("{\"type\":\"speakerIdentify\",\"name\":\"Mark\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.SpeakerIdentify.class);
        assertThat(((AvatarMessage.SpeakerIdentify) msg).name()).isEqualTo("Mark");
    }

    @Test
    void encodesSegmentMessage() {
        List<Cue> cues = List.of(new Cue.GestureCue("nod", 450L));
        var    visemes = List.of(new VisemeFrame("aa", 0, 200));
        var    segment = new AvatarMessage.Segment(1, 0, "Hello world", true, visemes, cues);
        String json    = MessageCodec.encode(segment);
        var    obj     = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("segment");
        assertThat(obj.get("turnSeq").getAsInt()).isEqualTo(1);
        assertThat(obj.get("index").getAsInt()).isEqualTo(0);
        assertThat(obj.get("text").getAsString()).isEqualTo("Hello world");
        assertThat(obj.get("hasAudio").getAsBoolean()).isTrue();
        assertThat(obj.getAsJsonArray("visemes")).hasSize(1);
        assertThat(obj.getAsJsonArray("cues")).hasSize(1);
    }

    @Test
    void encodesTurnCompleteMessage() {
        var    msg  = new AvatarMessage.TurnComplete(1, 2, TurnOrigin.USER);
        String json = MessageCodec.encode(msg);
        var    obj  = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("turnComplete");
        assertThat(obj.get("turnSeq").getAsInt()).isEqualTo(1);
        assertThat(obj.get("segmentCount").getAsInt()).isEqualTo(2);
        assertThat(obj.get("origin").getAsString()).isEqualTo("USER");
    }

    @Test
    void encodesGestureMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Gesture("nod", GestureOrigin.ATTENTION));
        var    obj  = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("gesture");
        assertThat(obj.get("tag").getAsString()).isEqualTo("nod");
        assertThat(obj.get("origin").getAsString()).isEqualTo("ATTENTION");
    }

    @Test
    void encodesMoodMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Mood("happy"));
        var    obj  = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("mood");
        assertThat(obj.get("state").getAsString()).isEqualTo("happy");
    }

    @Test
    void decodesPlaybackComplete() {
        var msg = MessageCodec.decodeClient("{\"type\":\"playbackComplete\",\"turnSeq\":1}");
        assertThat(msg).isInstanceOf(AvatarMessage.PlaybackComplete.class);
        assertThat(((AvatarMessage.PlaybackComplete) msg).turnSeq()).isEqualTo(1);
    }

    @Test
    void decodesUserActivity() {
        var msg = MessageCodec.decodeClient("{\"type\":\"userActivity\",\"kind\":\"TYPING\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.UserActivity.class);
        assertThat(((AvatarMessage.UserActivity) msg).kind()).isEqualTo(UserActivityKind.TYPING);
    }
}

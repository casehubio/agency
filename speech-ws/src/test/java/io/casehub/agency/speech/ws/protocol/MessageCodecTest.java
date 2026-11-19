package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode parse(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void encodesPartialMessage() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Partial("hello"));
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("partial");
        assertThat(node.get("text").asText()).isEqualTo("hello");
    }

    @Test
    void encodesTranscriptMessage() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Transcript("Hello world."));
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("transcript");
        assertThat(node.get("text").asText()).isEqualTo("Hello world.");
    }

    @Test
    void encodesErrorMessage() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Error("service down"));
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("error");
        assertThat(node.get("message").asText()).isEqualTo("service down");
    }

    @Test
    void encodesStartMessage() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Start(44100));
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("start");
        assertThat(node.get("sampleRate").asInt()).isEqualTo(44100);
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
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodesSpeakerPrompt() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.SpeakerPrompt("What's your name?"));
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("speakerPrompt");
        assertThat(node.get("message").asText()).isEqualTo("What's your name?");
    }

    @Test
    void encodesSpeakerIdentified() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.SpeakerIdentified("Mark", 0.95));
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("speakerIdentified");
        assertThat(node.get("name").asText()).isEqualTo("Mark");
        assertThat(node.get("confidence").asDouble()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void decodeSpeakerIdentify() {
        var msg = MessageCodec.decodeClient("{\"type\":\"speakerIdentify\",\"name\":\"Mark\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.SpeakerIdentify.class);
        assertThat(((AvatarMessage.SpeakerIdentify) msg).name()).isEqualTo("Mark");
    }

    @Test
    void encodesSegmentMessage() throws Exception {
        List<Cue> cues = List.of(new Cue.GestureCue("nod", 450L), new Cue.MoodCue("happy", 200L));
        var visemes = List.of(new VisemeFrame("aa", 0, 200));
        var segment = new AvatarMessage.Segment(1, 0, "Hello world", true, visemes, cues);
        String json = MessageCodec.encode(segment);
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("segment");
        assertThat(node.get("turnSeq").asInt()).isEqualTo(1);
        assertThat(node.get("index").asInt()).isEqualTo(0);
        assertThat(node.get("text").asText()).isEqualTo("Hello world");
        assertThat(node.get("hasAudio").asBoolean()).isTrue();
        assertThat(node.get("visemes")).hasSize(1);
        assertThat(node.get("cues")).hasSize(2);
        var gestureCue = node.get("cues").get(0);
        assertThat(gestureCue.get("type").asText()).isEqualTo("gesture");
        assertThat(gestureCue.get("tag").asText()).isEqualTo("nod");
        assertThat(gestureCue.get("offsetMs").asLong()).isEqualTo(450L);
        var moodCue = node.get("cues").get(1);
        assertThat(moodCue.get("type").asText()).isEqualTo("mood");
        assertThat(moodCue.get("mood").asText()).isEqualTo("happy");
        assertThat(moodCue.get("offsetMs").asLong()).isEqualTo(200L);
    }

    @Test
    void encodesTurnCompleteMessage() throws Exception {
        var msg = new AvatarMessage.TurnComplete(1, 2, TurnOrigin.USER);
        String json = MessageCodec.encode(msg);
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("turnComplete");
        assertThat(node.get("turnSeq").asInt()).isEqualTo(1);
        assertThat(node.get("segmentCount").asInt()).isEqualTo(2);
        assertThat(node.get("origin").asText()).isEqualTo("USER");
    }

    @Test
    void encodesGestureMessage() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Gesture("nod", GestureOrigin.ATTENTION));
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("gesture");
        assertThat(node.get("tag").asText()).isEqualTo("nod");
        assertThat(node.get("origin").asText()).isEqualTo("ATTENTION");
    }

    @Test
    void encodesMoodMessage() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Mood("happy"));
        var node = parse(json);
        assertThat(node.get("type").asText()).isEqualTo("mood");
        assertThat(node.get("state").asText()).isEqualTo("happy");
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

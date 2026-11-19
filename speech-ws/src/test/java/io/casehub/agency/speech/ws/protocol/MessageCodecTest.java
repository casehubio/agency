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

    @Test
    void encodesStartMessageOmitsNullFields() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Start(16000));
        var node = parse(json);
        assertThat(node.has("llmModel")).isFalse();
        assertThat(node.has("ttsModel")).isFalse();
    }

    @Test
    void encodesTextMessageOmitsNullFields() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Text("hello"));
        var node = parse(json);
        assertThat(node.has("llmModel")).isFalse();
        assertThat(node.has("ttsModel")).isFalse();
    }

    @Test
    void encodesTextMessageIncludesNonNullFields() throws Exception {
        String json = MessageCodec.encode(new AvatarMessage.Text("hello", "gpt-4", null));
        var node = parse(json);
        assertThat(node.get("llmModel").asText()).isEqualTo("gpt-4");
        assertThat(node.has("ttsModel")).isFalse();
    }

    // --- kebab-case type discriminator tests ---

    @Test
    void decodesKebabCasePlaybackComplete() {
        var msg = MessageCodec.decodeClient("{\"type\":\"playback-complete\",\"turnSeq\":3}");
        assertThat(msg).isInstanceOf(AvatarMessage.PlaybackComplete.class);
        assertThat(((AvatarMessage.PlaybackComplete) msg).turnSeq()).isEqualTo(3);
    }

    @Test
    void decodesKebabCaseUserActivity() {
        var msg = MessageCodec.decodeClient("{\"type\":\"user-activity\",\"kind\":\"TYPING\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.UserActivity.class);
        assertThat(((AvatarMessage.UserActivity) msg).kind()).isEqualTo(UserActivityKind.TYPING);
    }

    @Test
    void decodesKebabCaseSpeakerIdentify() {
        var msg = MessageCodec.decodeClient("{\"type\":\"speaker-identify\",\"name\":\"Mark\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.SpeakerIdentify.class);
        assertThat(((AvatarMessage.SpeakerIdentify) msg).name()).isEqualTo("Mark");
    }

    @Test
    void decodesKebabCaseSpeakerPrompt() throws Exception {
        var msg = MAPPER.readValue("{\"type\":\"speaker-prompt\",\"message\":\"Hello\"}", AvatarMessage.class);
        assertThat(msg).isInstanceOf(AvatarMessage.SpeakerPrompt.class);
        assertThat(((AvatarMessage.SpeakerPrompt) msg).message()).isEqualTo("Hello");
    }

    @Test
    void decodesKebabCaseTurnComplete() throws Exception {
        var msg = MAPPER.readValue(
                "{\"type\":\"turn-complete\",\"turnSeq\":2,\"segmentCount\":3,\"origin\":\"USER\"}",
                AvatarMessage.class);
        assertThat(msg).isInstanceOf(AvatarMessage.TurnComplete.class);
        var tc = (AvatarMessage.TurnComplete) msg;
        assertThat(tc.turnSeq()).isEqualTo(2);
        assertThat(tc.segmentCount()).isEqualTo(3);
    }

    // --- kebab-case field alias tests ---

    @Test
    void decodesKebabCaseFieldsInStart() {
        var msg = MessageCodec.decodeClient(
                "{\"type\":\"start\",\"sample-rate\":44100,\"llm-model\":\"gpt-4\",\"tts-model\":\"vits\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.Start.class);
        var start = (AvatarMessage.Start) msg;
        assertThat(start.sampleRate()).isEqualTo(44100);
        assertThat(start.llmModel()).isEqualTo("gpt-4");
        assertThat(start.ttsModel()).isEqualTo("vits");
    }

    @Test
    void decodesKebabCaseFieldsInText() {
        var msg = MessageCodec.decodeClient(
                "{\"type\":\"text\",\"text\":\"hello\",\"llm-model\":\"gpt-4\",\"tts-model\":\"vits\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.Text.class);
        var text = (AvatarMessage.Text) msg;
        assertThat(text.llmModel()).isEqualTo("gpt-4");
        assertThat(text.ttsModel()).isEqualTo("vits");
    }

    @Test
    void decodesKebabCasePlaybackCompleteFields() {
        var msg = MessageCodec.decodeClient(
                "{\"type\":\"playback-complete\",\"turn-seq\":5}");
        assertThat(msg).isInstanceOf(AvatarMessage.PlaybackComplete.class);
        assertThat(((AvatarMessage.PlaybackComplete) msg).turnSeq()).isEqualTo(5);
    }

    @Test
    void decodesKebabCaseSegmentFields() throws Exception {
        var msg = MAPPER.readValue(
                "{\"type\":\"segment\",\"turn-seq\":1,\"index\":0,\"text\":\"Hi\"," +
                "\"has-audio\":true,\"visemes\":[],\"cues\":[]}",
                AvatarMessage.class);
        assertThat(msg).isInstanceOf(AvatarMessage.Segment.class);
        var seg = (AvatarMessage.Segment) msg;
        assertThat(seg.turnSeq()).isEqualTo(1);
        assertThat(seg.hasAudio()).isTrue();
    }

    @Test
    void decodesKebabCaseTurnCompleteFields() throws Exception {
        var msg = MAPPER.readValue(
                "{\"type\":\"turn-complete\",\"turn-seq\":2,\"segment-count\":3,\"origin\":\"USER\"}",
                AvatarMessage.class);
        var tc = (AvatarMessage.TurnComplete) msg;
        assertThat(tc.turnSeq()).isEqualTo(2);
        assertThat(tc.segmentCount()).isEqualTo(3);
    }

    @Test
    void decodesKebabCaseVisemeFields() throws Exception {
        var msg = MAPPER.readValue(
                "{\"type\":\"segment\",\"turnSeq\":1,\"index\":0,\"text\":\"Hi\"," +
                "\"hasAudio\":true,\"visemes\":[{\"viseme\":\"aa\",\"start-ms\":0,\"end-ms\":200,\"weight\":1.0}],\"cues\":[]}",
                AvatarMessage.class);
        var seg = (AvatarMessage.Segment) msg;
        assertThat(seg.visemes()).hasSize(1);
        assertThat(seg.visemes().get(0).startMs()).isEqualTo(0);
        assertThat(seg.visemes().get(0).endMs()).isEqualTo(200);
    }

    @Test
    void decodesKebabCaseCueFields() throws Exception {
        var msg = MAPPER.readValue(
                "{\"type\":\"segment\",\"turnSeq\":1,\"index\":0,\"text\":\"Hi\"," +
                "\"hasAudio\":true,\"visemes\":[],\"cues\":[{\"type\":\"gesture\",\"tag\":\"nod\",\"offset-ms\":100,\"char-offset\":5}]}",
                AvatarMessage.class);
        var seg = (AvatarMessage.Segment) msg;
        assertThat(seg.cues()).hasSize(1);
        var cue = (Cue.GestureCue) seg.cues().get(0);
        assertThat(cue.offsetMs()).isEqualTo(100);
        assertThat(cue.charOffset()).isEqualTo(5);
    }
}

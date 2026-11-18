package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.TextToSpeech;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnExecutorTest {

    private TextToSpeech ttsService;
    private List<String> sentText;
    private List<byte[]> sentBinary;
    private FloorState floorState;
    private TurnExecutor executor;

    private static final PerformanceVocabulary EMPTY_VOCAB = new PerformanceVocabulary() {
        public Set<String> gestureTags() { return Set.of(); }
        public boolean isValidGesture(String tag) { return false; }
        public List<GestureEntry> gesturePromptEntries() { return List.of(); }
        public Set<String> allClipNames() { return Set.of(); }
        public List<MoodEntry> moods() { return List.of(); }
        public boolean isValidMood(String mood) { return false; }
    };

    @BeforeEach
    void setUp() {
        ttsService = mock(TextToSpeech.class);
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2, 3}, "wav", List.of()));
        sentText = new ArrayList<>();
        sentBinary = new ArrayList<>();
        floorState = new FloorState();

        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                prompt -> "I hear you.",
                null,
                ttsService,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                null);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void executeUserTurnSendsSegmentAndTurnComplete() {
        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"segment\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
        assertThat(sentBinary).isNotEmpty();
    }

    @Test
    void turnSeqIncrementsPerTurn() {
        executor.executeUserTurn(new UserTurn("first", null, null, null));
        executor.executeUserTurn(new UserTurn("second", null, null, null));

        var turnCompletes = sentText.stream()
                .filter(json -> json.contains("\"type\":\"turnComplete\""))
                .toList();
        assertThat(turnCompletes).hasSize(2);
        assertThat(turnCompletes.get(0)).contains("\"turnSeq\":1");
        assertThat(turnCompletes.get(1)).contains("\"turnSeq\":2");
    }

    @Test
    void executeUserTurnAddsToHistory() {
        executor.executeUserTurn(new UserTurn("hello", null, null, null));
        assertThat(executor.historySize()).isEqualTo(2);
    }

    @Test
    void executeUserTurnSendsLegacyResponseAndPhonemes() {
        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"response\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"phonemes\""));
    }

    @Test
    void streamingGeneratorProcessesPerSentence() {
        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("First sentence.");
            onSentence.accept("Second sentence.");
            return "First sentence. Second sentence.";
        };

        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                null,
                streamGen,
                ttsService,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                null);

        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        var segments = sentText.stream()
                .filter(json -> json.contains("\"type\":\"segment\""))
                .toList();
        assertThat(segments).hasSize(2);
        assertThat(sentBinary).hasSize(2);
    }

    @Test
    void turnCompleteAlwaysSentEvenOnLlmError() {
        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                prompt -> { throw new RuntimeException("LLM down"); },
                null,
                ttsService,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                null);

        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"error\""));
    }

    @Test
    void ttsFailureSendsSegmentWithoutAudioAndContinues() {
        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("Good sentence.");
            onSentence.accept("Bad sentence.");
            return "Good sentence. Bad sentence.";
        };

        var failTts = mock(TextToSpeech.class);
        var callCount = new AtomicInteger(0);
        when(failTts.synthesise(any(), any())).thenAnswer(inv -> {
            if (callCount.getAndIncrement() == 1) throw new RuntimeException("TTS failed");
            return new SynthesisResult(new byte[]{1}, "wav", List.of());
        });

        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                null, streamGen, failTts, Map.of(),
                sentText::add, sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB), floorState, null, null);

        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        var segments = sentText.stream()
                .filter(json -> json.contains("\"type\":\"segment\""))
                .toList();
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0)).contains("\"hasAudio\":true");
        assertThat(segments.get(1)).contains("\"hasAudio\":false");
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"error\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
    }

    @Test
    void noLlmProviderSendsErrorAndTurnComplete() {
        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                null, null,
                ttsService, Map.of(),
                sentText::add, sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB), floorState, null, null);

        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"error\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
    }

    @Test
    void proactiveTurnSuppressedWhenFloorStateSays() throws InterruptedException {
        floorState.onStart();
        executor.enqueueProactiveTurn("hello proactive", System.currentTimeMillis());

        Thread.sleep(300);

        assertThat(sentText).noneSatisfy(json ->
                assertThat(json).contains("hello proactive"));
    }

    @Test
    void proactiveTurnDroppedWhenStaleAtDequeue() throws InterruptedException {
        long past = System.currentTimeMillis() - 10_000;
        floorState.onUserActivity();
        executor.enqueueProactiveTurn("stale content", past);

        Thread.sleep(300);

        assertThat(sentText).noneSatisfy(json ->
                assertThat(json).contains("stale content"));
    }

    @Test
    void proactiveTurnExecutesWhenFloorIsClear() throws InterruptedException {
        executor.enqueueProactiveTurn("proactive hello", System.currentTimeMillis());

        Thread.sleep(300);

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"segment\""));
    }

    @Test
    void resolvesNamedTtsModel() {
        var altTts = mock(TextToSpeech.class);
        when(altTts.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{9, 8}, "wav", List.of()));

        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                prompt -> "reply",
                null,
                ttsService,
                Map.of("kokoro:af_heart", altTts),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                null);

        executor.executeUserTurn(new UserTurn("hello", null, null, "kokoro:af_heart"));

        org.mockito.Mockito.verify(altTts, org.mockito.Mockito.atLeastOnce()).synthesise(any(), any());
        org.mockito.Mockito.verify(ttsService, org.mockito.Mockito.never()).synthesise(any(), any());
    }

    @Test
    void onResponseCallbackFired() {
        var responses = new ArrayList<String>();
        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                prompt -> "LLM says hello.",
                null,
                ttsService,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                responses::add,
                null);

        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(responses).containsExactly("LLM says hello.");
    }

    @Test
    void floorStateTransitionsDuringTurn() {
        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(floorState.hasUnacknowledgedTurn()).isTrue();
        assertThat(floorState.shouldSuppressProactive()).isTrue();
    }
}

package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.ws.protocol.Cue;
import io.casehub.agency.core.yaml.compiler.CompiledSpeechPipeline;
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
                CompiledSpeechPipeline.EMPTY);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void executeUserTurnSendsSegmentAndTurnComplete() {
        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"segment\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
        assertThat(sentBinary).isNotEmpty();
        assertThat(sentBinary.get(0)[1]).as("non-streaming isFinal").isEqualTo((byte) 1);
    }

    @Test
    void turnSeqIncrementsPerTurn() {
        executor.executeUserTurnBlocking(new UserTurn("first", null, null, null));
        executor.executeUserTurnBlocking(new UserTurn("second", null, null, null));

        var turnCompletes = sentText.stream()
                .filter(json -> json.contains("\"type\":\"turnComplete\""))
                .toList();
        assertThat(turnCompletes).hasSize(2);
        assertThat(turnCompletes.get(0)).contains("\"turnSeq\":1");
        assertThat(turnCompletes.get(1)).contains("\"turnSeq\":2");
    }

    @Test
    void executeUserTurnAddsToHistory() {
        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));
        assertThat(executor.historySize()).isEqualTo(2);
    }

    @Test
    void executeUserTurnSendsSegmentAndTurnCompleteNotLegacy() {
        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

        assertThat(sentText).noneSatisfy(json ->
                                                 assertThat(json).contains("\"type\":\"response\""));
        assertThat(sentText).noneSatisfy(json ->
                                                 assertThat(json).contains("\"type\":\"phonemes\""));
        assertThat(sentText).anySatisfy(json ->
                                                assertThat(json).contains("\"type\":\"segment\""));
        assertThat(sentText).anySatisfy(json ->
                                                assertThat(json).contains("\"type\":\"turnComplete\""));
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
                CompiledSpeechPipeline.EMPTY);

        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

        var segments = sentText.stream()
                .filter(json -> json.contains("\"type\":\"segment\""))
                .toList();
        assertThat(segments).hasSize(2);
        assertThat(sentBinary).hasSize(2);
    }

    @Test
    void streamingTtsUsesEstimatedVisemesAndStreamsChunks() {
        var streamingTts = mock(io.casehub.connectors.voice.spi.StreamingTextToSpeech.class);
        when(streamingTts.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2, 3}, "wav", List.of()));
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<byte[]> onChunk = invocation.getArgument(2);
            onChunk.accept(new byte[]{10, 20});
            onChunk.accept(new byte[]{30, 40});
            return null;
        }).when(streamingTts).synthesiseStreaming(any(), any(), any());

        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                prompt -> "Streaming test.",
                null,
                streamingTts,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                CompiledSpeechPipeline.EMPTY);

        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

        var segments = sentText.stream()
                               .filter(json -> json.contains("\"type\":\"segment\""))
                               .toList();
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0)).contains("\"hasAudio\":true");

        // Streaming TTS sends 2 audio chunks, each with 8-byte header;
        // only the last chunk has isFinal=1
        assertThat(sentBinary).hasSize(2);
        assertThat(sentBinary.get(0).length).isEqualTo(8 + 2);
        assertThat(sentBinary.get(0)[1]).as("first chunk isFinal").isEqualTo((byte) 0);
        assertThat(sentBinary.get(1).length).isEqualTo(8 + 2);
        assertThat(sentBinary.get(1)[1]).as("last chunk isFinal").isEqualTo((byte) 1);
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
                CompiledSpeechPipeline.EMPTY);

        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

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
                new UtteranceSegmenter(EMPTY_VOCAB), floorState, null, CompiledSpeechPipeline.EMPTY);

        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

        var segments = sentText.stream()
                .filter(json -> json.contains("\"type\":\"segment\""))
                .toList();
        assertThat(segments).hasSize(2);
        assertThat(segments).anySatisfy(s -> assertThat(s).contains("\"hasAudio\":true"));
        assertThat(segments).anySatisfy(s -> assertThat(s).contains("\"hasAudio\":false"));
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
                new UtteranceSegmenter(EMPTY_VOCAB), floorState, null, CompiledSpeechPipeline.EMPTY);

        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

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
                CompiledSpeechPipeline.EMPTY);

        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, "kokoro:af_heart"));

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
                CompiledSpeechPipeline.EMPTY);

        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

        assertThat(responses).containsExactly("LLM says hello.");
    }

    @Test
    void floorStateTransitionsDuringTurn() {
        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

        assertThat(floorState.hasUnacknowledgedTurn()).isTrue();
        assertThat(floorState.shouldSuppressProactive()).isTrue();
    }

    @Test
    void expiryScalesWithResponseLength() {
        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                prompt -> "This is a longer response that takes more audio time to play back for the user.",
                null,
                ttsService,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                CompiledSpeechPipeline.EMPTY);

        long before = System.currentTimeMillis();
        executor.executeUserTurnBlocking(new UserTurn("hello", null, null, null));

        long expiresAt = floorState.expiresAt();
        long elapsed = expiresAt - before;
        // 80 chars × 80ms/char = 6400ms audio + 6400/0.6 ≈ 10667ms + 5000ms buffer ≈ 15667ms
        // Must be significantly more than the old fixed 5000ms
        assertThat(elapsed).as("expiry includes estimated audio duration")
                           .isGreaterThan(10_000);
    }

    @Test
    void estimateMarkTimingAppliesCharProportionalOffset() {
        List<Cue> cues = List.of(
                new Cue.GestureCue("nod", 0L, 10),
                new Cue.GestureCue("wave", 0L, 30));
        var estimated = TurnExecutor.estimateMarkTiming(cues, 40, 4000L);
        assertThat(estimated).hasSize(2);
        assertThat(((Cue.GestureCue) estimated.get(0)).offsetMs()).isEqualTo(1000L);
        assertThat(((Cue.GestureCue) estimated.get(1)).offsetMs()).isEqualTo(3000L);
    }

    @Test
    void estimateMarkTimingPreservesNegativeCharOffset() {
        List<Cue> cues = List.of(new Cue.GestureCue("nod", 0L, -1));
        var estimated = TurnExecutor.estimateMarkTiming(cues, 40, 4000L);
        assertThat(estimated).hasSize(1);
        assertThat(((Cue.GestureCue) estimated.get(0)).offsetMs()).isEqualTo(0L);
    }

    @Test
    void estimateMarkTimingEmptyCuesPassThrough() {
        assertThat(TurnExecutor.estimateMarkTiming(List.of(), 40, 4000L)).isEmpty();
    }

    @Test
    void estimateMarkTimingHandlesMoodCues() {
        List<Cue> cues = List.of(new Cue.MoodCue("happy", 0L, 20));
        var estimated = TurnExecutor.estimateMarkTiming(cues, 40, 4000L);
        assertThat(((Cue.MoodCue) estimated.get(0)).offsetMs()).isEqualTo(2000L);
    }
}

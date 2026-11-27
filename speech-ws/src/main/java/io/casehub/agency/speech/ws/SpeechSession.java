package io.casehub.agency.speech.ws;

import io.casehub.agency.core.yaml.compiler.CompiledSpeechPipeline;
import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.SpeechPromptAssembler;
import io.casehub.agency.speech.ws.protocol.AvatarMessage;
import io.casehub.agency.speech.ws.protocol.MessageCodec;
import io.casehub.connectors.voice.model.SpeakerEmbedding;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.spi.RecognitionStream;
import io.casehub.connectors.voice.spi.SpeakerEmbeddingExtractor;
import io.casehub.connectors.voice.spi.SpeakerRegistry;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.eidos.api.AssembledPrompt;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class SpeechSession {
    @FunctionalInterface
    public interface StreamingResponseGenerator {
        String generate(AssembledPrompt prompt, Consumer<String> onSentence);
    }

    private static final System.Logger LOG = System.getLogger("speech-session");

    private static final PerformanceVocabulary EMPTY_VOCAB = new PerformanceVocabulary() {
        public Set<String> gestureTags() { return Set.of(); }
        public boolean isValidGesture(String tag) { return false; }
        public List<GestureEntry> gesturePromptEntries() { return List.of(); }
        public Set<String> allClipNames() { return Set.of(); }
        public List<MoodEntry> moods() { return List.of(); }
        public boolean isValidMood(String mood) { return false; }
    };

    // --- Capture-side state ---
    private final StreamingSpeechToText sttService;
    private final CompiledSpeechPipeline pipeline;
    private final Consumer<String> textSink;
    private final Consumer<byte[]> binarySink;
    private final java.util.function.Supplier<String> vocabularyHintSupplier;

    private @Nullable RecognitionStream stream;
    private int sampleRate = 16000;
    private volatile boolean polling;
    private @Nullable Thread pollingThread;
    private @Nullable String activeLlmModel;
    private @Nullable String activeTtsModel;

    private static final int RING_BUFFER_SECONDS = 5;
    private static final int RING_BUFFER_SIZE = 16000 * RING_BUFFER_SECONDS;
    private static final int MIN_SAMPLES_FOR_EMBEDDING = 16000 * 3 / 2;

    private @Nullable SpeakerEmbeddingExtractor embeddingExtractor;
    private @Nullable SpeakerRegistry speakerRegistry;
    private @Nullable String identifiedSpeaker;
    private volatile boolean isClosed;
    private io.casehub.connectors.voice.spi.@Nullable EndpointDetectionPolicy endpointPolicy;
    private volatile VoiceState voiceState = VoiceState.LISTENING;
    private List<byte[]> cannedResponses = List.of();

    private final float[] ringBuffer = new float[RING_BUFFER_SIZE];
    private int ringBufferPos = 0;
    private @Nullable SpeakerEmbedding pendingEmbedding;
    private boolean enrollmentPending;
    private @Nullable String explicitEnrollmentName;

    // --- Turn-side delegation ---
    private final TurnExecutor turnExecutor;
    private final FloorState floorState;

    // --- Cognition (optional, straddles both sides) ---
    private io.casehub.agency.speech.AvatarCognition cognition;
    private @Nullable String cognitionAgentId;
    private @Nullable String cognitionTenantId;


    public SpeechSession(StreamingSpeechToText sttService,
                         TextToSpeech ttsService,
                         CompiledSpeechPipeline pipeline,
                         @Nullable Function<AssembledPrompt, String> responseGenerator,
                         SpeechPromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink) {
        this(sttService, ttsService, pipeline, responseGenerator, promptAssembler,
             textSink, binarySink, Map.of());
    }

    public SpeechSession(StreamingSpeechToText sttService,
                         TextToSpeech ttsService,
                         CompiledSpeechPipeline pipeline,
                         @Nullable Function<AssembledPrompt, String> responseGenerator,
                         SpeechPromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink,
                         Map<String, TextToSpeech> ttsModels) {
        this(sttService, ttsService, pipeline, responseGenerator, promptAssembler,
             textSink, binarySink, ttsModels, null, null);
    }

    public SpeechSession(StreamingSpeechToText sttService,
                         TextToSpeech ttsService,
                         CompiledSpeechPipeline pipeline,
                         @Nullable Function<AssembledPrompt, String> responseGenerator,
                         SpeechPromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink,
                         Map<String, TextToSpeech> ttsModels,
                         Consumer<String> onResponse,
                         java.util.function.Supplier<String> vocabularyHintSupplier) {
        this(sttService, ttsService, pipeline, responseGenerator, null,
             promptAssembler, textSink, binarySink, ttsModels, onResponse, vocabularyHintSupplier, null);
    }

    public SpeechSession(StreamingSpeechToText sttService,
                         TextToSpeech ttsService,
                         CompiledSpeechPipeline pipeline,
                         @Nullable Function<AssembledPrompt, String> responseGenerator,
                         @Nullable StreamingResponseGenerator streamingGenerator,
                         SpeechPromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink,
                         Map<String, TextToSpeech> ttsModels,
                         Consumer<String> onResponse,
                         java.util.function.Supplier<String> vocabularyHintSupplier,
                         PerformanceVocabulary vocabulary) {
        this.sttService             = sttService;
        this.pipeline               = pipeline;
        this.textSink               = textSink;
        this.binarySink             = binarySink;
        this.vocabularyHintSupplier = vocabularyHintSupplier;

        this.floorState   = new FloorState();
        this.turnExecutor = new TurnExecutor(
                promptAssembler, responseGenerator, streamingGenerator,
                ttsService, ttsModels, textSink, binarySink,
                new UtteranceSegmenter(vocabulary != null ? vocabulary : EMPTY_VOCAB), floorState,
                onResponse, pipeline);
    }


    public void setMinPlaybackRate(double rate) { turnExecutor.setMinPlaybackRate(rate); }

    public void handleStart(AvatarMessage.Start msg) {
        stopPolling();
        if (stream != null) {
            stream.close();
        }
        floorState.onStart();
        this.sampleRate = msg.sampleRate();
        this.activeLlmModel = msg.llmModel();
        this.activeTtsModel = msg.ttsModel();
        var opts = TranscriptionOptions.defaults();
        if (vocabularyHintSupplier != null) {
            String hint = vocabularyHintSupplier.get();
            if (hint != null && !hint.isEmpty()) {
                opts = opts.withVocabularyHint(hint);
            }
        }
        stream = sttService.startStream(opts);
        voiceState = VoiceState.LISTENING;
        sendVoiceState(VoiceState.LISTENING);
        startPolling();
    }

    public SpeechSession withSpeakerServices(
            @Nullable SpeakerEmbeddingExtractor embeddingExtractor,
            @Nullable SpeakerRegistry speakerRegistry) {
        this.embeddingExtractor = embeddingExtractor;
        this.speakerRegistry = speakerRegistry;
        return this;
    }

    public SpeechSession withAvatarCognition(
            io.casehub.agency.speech.AvatarCognition cognition,
            String agentId, String tenantId) {
        this.cognition = cognition;
        this.cognitionAgentId = agentId;
        this.cognitionTenantId = tenantId;
        return this;
    }

    public SpeechSession withEndpointPolicy(
            io.casehub.connectors.voice.spi.EndpointDetectionPolicy policy) {
        this.endpointPolicy = policy;
        return this;
    }

    public SpeechSession withCannedResponses(List<byte[]> preSynthesised) {
        this.cannedResponses = List.copyOf(preSynthesised);
        return this;
    }

    public VoiceState voiceState() { return voiceState; }

    public void handleProactiveSpeech(String content) {
        turnExecutor.enqueueProactiveTurn(content, System.currentTimeMillis());
    }

    public boolean isClosed() { return isClosed; }

    FloorState floorState()   {return floorState;}


    public int historySize() { return turnExecutor.historySize(); }

    public boolean shouldSuppressProactive() {
        return floorState.shouldSuppressProactive();
    }

    public String currentSubjectId() {
        return identifiedSpeaker != null ? identifiedSpeaker : "anonymous";
    }

    public void handleAudio(float[] samples) {
        if (stream != null) {
            stream.acceptSamples(samples, sampleRate);
        }
        if (embeddingExtractor != null && ringBufferPos < RING_BUFFER_SIZE) {
            int toCopy = Math.min(samples.length, RING_BUFFER_SIZE - ringBufferPos);
            System.arraycopy(samples, 0, ringBuffer, ringBufferPos, toCopy);
            ringBufferPos += toCopy;
        }
    }

    public void handleStop() {
        stopPolling();
        turnExecutor.cancelCurrentTurn();
        if (stream == null) {
            send(new AvatarMessage.Error("No active recording session"));
            return;
        }
        try {
            LOG.log(System.Logger.Level.INFO, "[STT] finalResult() called");
            TranscriptionResult result = stream.finalResult();
            stream.close();
            stream = null;

            String raw = result.text();
            LOG.log(System.Logger.Level.INFO, "[STT] raw: \"{0}\"", raw);

            String cleanText = ensureTrailingPunctuation(pipeline.postSttChain().apply(raw));
            LOG.log(System.Logger.Level.INFO, "[STT] after cleanup: \"{0}\"", cleanText);
            send(new AvatarMessage.Transcript(cleanText));
            floorState.onStopOrText();

            String speakerLabel = identifySpeaker();

            turnExecutor.executeUserTurnBlocking(
                    new UserTurn(cleanText, speakerLabel, activeLlmModel, activeTtsModel));

            recordCognitionInteraction(cleanText);
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        }
    }

    public void handleText(String text) {
        handleText(text, null, null);
    }

    public void handleText(String text, @Nullable String llmModel, @Nullable String ttsModel) {
        try {
            String cleanText = pipeline.postSttChain().apply(text);
            send(new AvatarMessage.Transcript(cleanText));
            floorState.onStopOrText();

            turnExecutor.executeUserTurnBlocking(
                    new UserTurn(cleanText, null, llmModel, ttsModel));

            recordCognitionInteraction(cleanText);
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        }
    }

    public void handlePlaybackComplete(int turnSeq) {
        floorState.onPlaybackComplete(turnSeq);
        if (voiceState == VoiceState.PLAYBACK) {
            voiceState = VoiceState.LISTENING;
            sendVoiceState(VoiceState.LISTENING);
        }
    }

    public void handleUserActivity() {
        floorState.onUserActivity();
    }

    public void handleSpeakerIdentify(String name) {
        if (pendingEmbedding != null && speakerRegistry != null) {
            speakerRegistry.register(name, pendingEmbedding);
            send(new AvatarMessage.SpeakerIdentified(name, 1.0));
            pendingEmbedding = null;
            enrollmentPending = false;
        } else if (explicitEnrollmentName == null) {
            explicitEnrollmentName = name;
        }
    }


    private @Nullable String identifySpeaker() {
        if (embeddingExtractor == null || speakerRegistry == null || ringBufferPos < MIN_SAMPLES_FOR_EMBEDDING) {
            ringBufferPos = 0;
            return null;
        }
        try {
            float[] audioForEmbedding = java.util.Arrays.copyOf(ringBuffer, ringBufferPos);
            var embedding = embeddingExtractor.extract(audioForEmbedding, 16000);

            if (explicitEnrollmentName != null) {
                speakerRegistry.register(explicitEnrollmentName, embedding);
                send(new AvatarMessage.SpeakerIdentified(explicitEnrollmentName, 1.0));
                String name = explicitEnrollmentName;
                explicitEnrollmentName = null;
                ringBufferPos = 0;
                return name;
            }

            var match = speakerRegistry.identify(embedding, 0.7);
            if (match.isPresent()) {
                send(new AvatarMessage.SpeakerIdentified(match.get().name(), match.get().confidence()));
                ringBufferPos = 0;
                return match.get().name();
            }

            pendingEmbedding = embedding;
            if (!enrollmentPending) {
                enrollmentPending = true;
                send(new AvatarMessage.SpeakerPrompt("I don't recognise your voice — what's your name?"));
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Speaker identification failed: " + e.getMessage());
        }
        ringBufferPos = 0;
        return null;
    }

    private void recordCognitionInteraction(String userMessage) {
        if (cognition != null && cognitionAgentId != null && cognitionTenantId != null) {
            var text = turnExecutor.lastAssistantText();
            var lastAssistant = text != null ? text : "";
            try {
                cognition.recordInteraction(cognitionAgentId, cognitionTenantId,
                        currentSubjectId(), userMessage, lastAssistant);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Signal recording failed", e);
            }
        }
    }

    static String ensureTrailingPunctuation(String text) {
        if (text.isEmpty()) { return text; }
        char last = text.charAt(text.length() - 1);
        if (last == '.' || last == '!' || last == '?' || last == '"' || last == '\'' || last == ')') {
            return text;
        }
        return text + ".";
    }

    private void startPolling() {
        polling       = true;
        pollingThread = Thread.ofVirtual().name("stt-partial-poll").start(() -> {
            String lastPartial = "";
            while (polling && stream != null) {
                try {
                    String partial = stream.partialResult();
                    if (partial != null && !partial.isEmpty() && !partial.equals(lastPartial)) {
                        lastPartial = partial;
                        send(new AvatarMessage.Partial(partial));
                    }
                    boolean hasNewSpeech = partial != null && !partial.isEmpty();
                    if (hasNewSpeech && (voiceState == VoiceState.PROCESSING || voiceState == VoiceState.PLAYBACK)) {
                        turnExecutor.cancelCurrentTurn();
                        TranscriptionResult interrupted = stream.segmentResult();
                        lastPartial = "";
                        String interruptedText = interrupted.text();
                        if (interruptedText != null && !interruptedText.isBlank()) {
                            send(new AvatarMessage.Transcript(interruptedText));
                        }
                        voiceState = VoiceState.LISTENING;
                        sendVoiceState(VoiceState.LISTENING);
                        playCannedResponse();
                    } else if (endpointPolicy != null && hasNewSpeech && voiceState == VoiceState.LISTENING) {
                        var action = endpointPolicy.evaluate(stream, partial);
                        if (action != io.casehub.connectors.voice.spi.EndpointAction.CONTINUE) {
                            voiceState = VoiceState.ENDPOINT_DETECTED;
                            sendVoiceState(VoiceState.ENDPOINT_DETECTED);
                            turnExecutor.cancelCurrentTurn();
                            TranscriptionResult segment = stream.segmentResult();
                            lastPartial = "";
                            String cleanText = ensureTrailingPunctuation(
                                    pipeline.postSttChain().apply(segment.text()));
                            if (!cleanText.isBlank()) {
                                voiceState = VoiceState.PROCESSING;
                                sendVoiceState(VoiceState.PROCESSING);
                                send(new AvatarMessage.Transcript(cleanText));
                                floorState.onStopOrText();
                                String speakerLabel = identifySpeaker();
                                var turnFuture = turnExecutor.executeUserTurn(
                                        new UserTurn(cleanText, speakerLabel,
                                                     activeLlmModel, activeTtsModel));
                                recordCognitionInteraction(cleanText);
                                Thread.ofVirtual().name("voice-state-playback").start(() -> {
                                    try { turnFuture.get(); } catch (Exception ignored) {}
                                    if (voiceState == VoiceState.PROCESSING) {
                                        voiceState = VoiceState.PLAYBACK;
                                        sendVoiceState(VoiceState.PLAYBACK);
                                    }
                                });
                            } else {
                                voiceState = VoiceState.LISTENING;
                                sendVoiceState(VoiceState.LISTENING);
                            }
                        }
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void stopPolling() {
        polling = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
            try {
                pollingThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pollingThread = null;
        }
    }

    private void sendVoiceState(VoiceState state) {
        send(new AvatarMessage.VoiceStateChange(state.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
    }

    private void playCannedResponse() {
        if (cannedResponses.isEmpty()) return;
        int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(cannedResponses.size());
        byte[] audio = cannedResponses.get(idx);
        var buf = java.nio.ByteBuffer.allocate(8 + audio.length)
                                     .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);
        buf.put((byte) 1);
        buf.putShort((short) 0);
        buf.putInt(-1);
        buf.put(audio);
        binarySink.accept(buf.array());
    }

    private void send(AvatarMessage msg) {
        textSink.accept(MessageCodec.encode(msg));
    }

    public void close() {
        isClosed = true;
        stopPolling();
        turnExecutor.close();
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }
}

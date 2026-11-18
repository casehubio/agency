package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.AssembledPrompt;
import io.casehub.agency.speech.ConversationTurn;
import io.casehub.agency.speech.PromptContext;
import io.casehub.agency.speech.SpeechPromptAssembler;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.agency.speech.ws.protocol.AvatarMessage;
import io.casehub.agency.speech.ws.protocol.MessageCodec;
import io.casehub.agency.speech.ws.protocol.TurnOrigin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public class TurnExecutor implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger("turn-executor");
    private static final long DEFAULT_EXPIRY_MS = 5000;

    private final SpeechPromptAssembler assembler;
    private final @Nullable Function<AssembledPrompt, String> responseGenerator;
    private final SpeechSession.@Nullable StreamingResponseGenerator streamingGenerator;
    private final TextToSpeech defaultTts;
    private final Map<String, TextToSpeech> ttsModels;
    private final Consumer<String> textSink;
    private final Consumer<byte[]> binarySink;
    private final UtteranceSegmenter segmenter;
    private final FloorState floorState;
    private final @Nullable Consumer<String> onResponse;
    private final @Nullable PromptContext promptContext;
    private final List<ConversationTurn> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("turn-executor").factory());

    private int turnSeq;

    public TurnExecutor(SpeechPromptAssembler assembler,
                        @Nullable Function<AssembledPrompt, String> responseGenerator,
                        SpeechSession.@Nullable StreamingResponseGenerator streamingGenerator,
                        TextToSpeech defaultTts,
                        Map<String, TextToSpeech> ttsModels,
                        Consumer<String> textSink,
                        Consumer<byte[]> binarySink,
                        UtteranceSegmenter segmenter,
                        FloorState floorState,
                        @Nullable Consumer<String> onResponse,
                        @Nullable PromptContext promptContext) {
        this.assembler = assembler;
        this.responseGenerator = responseGenerator;
        this.streamingGenerator = streamingGenerator;
        this.defaultTts = defaultTts;
        this.ttsModels = ttsModels;
        this.textSink = textSink;
        this.binarySink = binarySink;
        this.segmenter = segmenter;
        this.floorState = floorState;
        this.onResponse = onResponse;
        this.promptContext = promptContext;
    }

    public void executeUserTurn(UserTurn turn) {
        try {
            executor.submit(() -> processUserTurn(turn)).get();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Turn execution failed", e);
        }
    }

    public void enqueueProactiveTurn(String content, long evaluatedAt) {
        executor.submit(() -> processProactiveTurn(content, evaluatedAt));
    }

    int historySize() { return history.size(); }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void processUserTurn(UserTurn turn) {
        floorState.onTurnStart();
        int currentTurnSeq = ++turnSeq;
        int[] segmentIndex = {0};

        try {
            history.add(new ConversationTurn("user", turn.cleanText(), turn.speakerLabel()));
            TextToSpeech tts = resolveTts(turn.ttsModel());
            AssembledPrompt prompt = assemblePrompt(turn);

            String responseText;
            if (streamingGenerator != null) {
                responseText = streamingGenerator.generate(prompt, sentence -> {
                    var segments = segmenter.segment(sentence);
                    for (var seg : segments) {
                        synthesiseAndSendSegment(tts, seg, currentTurnSeq, segmentIndex[0]++);
                    }
                });
            } else if (responseGenerator != null) {
                responseText = responseGenerator.apply(prompt);
                if (responseText != null) {
                    var segments = segmenter.segment(responseText);
                    for (var seg : segments) {
                        synthesiseAndSendSegment(tts, seg, currentTurnSeq, segmentIndex[0]++);
                    }
                }
            } else {
                send(new AvatarMessage.Error("No LLM provider configured"));
                responseText = null;
            }

            if (responseText != null) {
                send(new AvatarMessage.Response(responseText));
                history.add(new ConversationTurn("assistant", responseText));
                if (onResponse != null) onResponse.accept(responseText);
            }
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        } finally {
            floorState.onTurnEnd();
            long expiresAt = System.currentTimeMillis() + DEFAULT_EXPIRY_MS;
            floorState.onTurnComplete(currentTurnSeq, expiresAt);
            send(new AvatarMessage.TurnComplete(currentTurnSeq, segmentIndex[0],
                    TurnOrigin.USER));
        }
    }

    private void processProactiveTurn(String content, long evaluatedAt) {
        if (floorState.shouldSuppressProactive()) {
            LOG.log(System.Logger.Level.DEBUG, "Proactive turn suppressed — floor busy");
            return;
        }
        if (floorState.lastUserActivity() > evaluatedAt) {
            LOG.log(System.Logger.Level.DEBUG,
                    "Proactive turn dropped — user activity after evaluation");
            return;
        }

        floorState.onTurnStart();
        int currentTurnSeq = ++turnSeq;
        int[] segmentIndex = {0};

        try {
            TextToSpeech tts = resolveTts(null);
            var segments = segmenter.segment(content);
            for (var seg : segments) {
                synthesiseAndSendSegment(tts, seg, currentTurnSeq, segmentIndex[0]++);
            }
            send(new AvatarMessage.Response(content));
            history.add(new ConversationTurn("assistant", content));
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        } finally {
            floorState.onTurnEnd();
            long expiresAt = System.currentTimeMillis() + DEFAULT_EXPIRY_MS;
            floorState.onTurnComplete(currentTurnSeq, expiresAt);
            send(new AvatarMessage.TurnComplete(currentTurnSeq, segmentIndex[0],
                    TurnOrigin.PROACTIVE));
        }
    }

    private void synthesiseAndSendSegment(TextToSpeech tts, ParsedSegment seg,
                                           int currentTurnSeq, int index) {
        try {
            var synthesis = tts.synthesise(seg.cleanText(),
                    new SynthesisOptions(null, null, "wav", true));
            var visemes = VisemeMapping.convert(synthesis.phonemes());

            send(new AvatarMessage.Segment(currentTurnSeq, index, seg.cleanText(),
                    true, visemes, seg.cues()));
            send(new AvatarMessage.Phonemes(visemes));
            binarySink.accept(synthesis.audioData());
        } catch (Exception e) {
            send(new AvatarMessage.Segment(currentTurnSeq, index, seg.cleanText(),
                    false, List.of(), List.of()));
            send(new AvatarMessage.Error("TTS failed for segment " + index
                    + ": " + e.getMessage()));
        }
    }

    private AssembledPrompt assemblePrompt(UserTurn turn) {
        AssembledPrompt prompt = promptContext != null
                ? assembler.assemble(turn.cleanText(), List.copyOf(history), promptContext)
                : assembler.assemble(turn.cleanText(), List.copyOf(history));
        if (turn.llmModel() != null) {
            prompt = new AssembledPrompt(prompt.systemPrompt(), prompt.userPrompt(),
                    turn.llmModel());
        }
        return prompt;
    }

    private TextToSpeech resolveTts(@Nullable String modelKey) {
        if (modelKey != null && ttsModels.containsKey(modelKey)) {
            return ttsModels.get(modelKey);
        }
        return defaultTts;
    }

    private void send(AvatarMessage msg) {
        textSink.accept(MessageCodec.encode(msg));
    }
}

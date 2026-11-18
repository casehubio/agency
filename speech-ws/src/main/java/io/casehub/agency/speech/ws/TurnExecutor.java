package io.casehub.agency.speech.ws;

import io.casehub.eidos.api.AssembledPrompt;
import io.casehub.agency.speech.ConversationTurn;

import io.casehub.agency.speech.SpeechPromptAssembler;
import io.casehub.agency.speech.ws.protocol.AvatarMessage;
import io.casehub.agency.speech.ws.protocol.Cue;
import io.casehub.agency.speech.ws.protocol.MessageCodec;
import io.casehub.agency.speech.ws.protocol.TurnOrigin;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.TextMark;
import io.casehub.connectors.voice.spi.TextToSpeech;
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
    private static final long EXPIRY_BUFFER_MS = 5000;
    private static final long MS_PER_CHAR = 80;

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

    private final List<ConversationTurn> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("turn-executor").factory());

    private int turnSeq;
    private double minPlaybackRate = 0.6;
    private long turnEstimatedAudioMs;

    public TurnExecutor(SpeechPromptAssembler assembler,
                        @Nullable Function<AssembledPrompt, String> responseGenerator,
                        SpeechSession.@Nullable StreamingResponseGenerator streamingGenerator,
                        TextToSpeech defaultTts,
                        Map<String, TextToSpeech> ttsModels,
                        Consumer<String> textSink,
                        Consumer<byte[]> binarySink,
                        UtteranceSegmenter segmenter,
                        FloorState floorState,
                        @Nullable Consumer<String> onResponse) {
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

    }

    void setMinPlaybackRate(double rate) { this.minPlaybackRate = rate; }

    public void executeUserTurn(UserTurn turn) {
        try {
            executor.submit(() -> processUserTurn(turn)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING, "Turn execution interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            LOG.log(System.Logger.Level.WARNING, "Turn execution failed", e.getCause());
        }
    }

    public void enqueueProactiveTurn(String content, long evaluatedAt) {
        executor.submit(() -> processProactiveTurn(content, evaluatedAt));
    }

    int historySize() { return history.size(); }

    @Nullable String lastAssistantText() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equals(history.get(i).role())) {
                return history.get(i).text();
            }
        }
        return null;
    }


    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void processUserTurn(UserTurn turn) {
        floorState.onTurnStart();
        int currentTurnSeq = ++turnSeq;
        int[] segmentIndex = {0};
        turnEstimatedAudioMs = 0;

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
                history.add(new ConversationTurn("assistant", responseText));
                if (onResponse != null) onResponse.accept(responseText);
            }
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        } finally {
            long expiresAt = System.currentTimeMillis()
                    + (long) (turnEstimatedAudioMs / minPlaybackRate) + EXPIRY_BUFFER_MS;
            floorState.onTurnComplete(currentTurnSeq, expiresAt);
            floorState.onTurnEnd();
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
        turnEstimatedAudioMs = 0;

        try {
            TextToSpeech tts = resolveTts(null);
            var segments = segmenter.segment(content);
            for (var seg : segments) {
                synthesiseAndSendSegment(tts, seg, currentTurnSeq, segmentIndex[0]++);
            }
            history.add(new ConversationTurn("assistant", content));
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        } finally {
            long expiresAt = System.currentTimeMillis()
                    + (long) (turnEstimatedAudioMs / minPlaybackRate) + EXPIRY_BUFFER_MS;
            floorState.onTurnComplete(currentTurnSeq, expiresAt);
            floorState.onTurnEnd();
            send(new AvatarMessage.TurnComplete(currentTurnSeq, segmentIndex[0],
                    TurnOrigin.PROACTIVE));
        }
    }

    private void synthesiseAndSendSegment(TextToSpeech tts, ParsedSegment seg,
                                          int currentTurnSeq, int index) {
        turnEstimatedAudioMs += seg.cleanText().length() * MS_PER_CHAR;
        try {
            if (tts instanceof io.casehub.connectors.voice.spi.StreamingTextToSpeech streamingTts) {
                long estimatedMs = seg.cleanText().length() * MS_PER_CHAR;
                var  visemes     = VisemeMapping.estimateFromText(seg.cleanText(), estimatedMs);
                var  timedCues  = estimateMarkTiming(seg.cues(), seg.cleanText().length(), estimatedMs);
                send(new AvatarMessage.Segment(currentTurnSeq, index, seg.cleanText(),
                                               true, visemes, timedCues));
                byte[][] lastChunk = {null};
                streamingTts.synthesiseStreaming(seg.cleanText(),
                                                 new SynthesisOptions(null, null, "wav", false),
                                                 chunk -> {
                                                     if (lastChunk[0] != null) {
                                                         sendAudioFrame(lastChunk[0], currentTurnSeq, index, false);
                                                     }
                                                     lastChunk[0] = chunk;
                                                 });
                if (lastChunk[0] != null) {
                    sendAudioFrame(lastChunk[0], currentTurnSeq, index, true);
                }
            } else {
                var marks = buildTextMarks(seg.cues(), seg.cleanText().length());
                var synthesis = tts.synthesise(seg.cleanText(),
                                               new SynthesisOptions(null, null, "wav", true, marks));
                var visemes = VisemeMapping.convert(synthesis.phonemes());
                var timedCues = applyMarkTiming(seg.cues(), synthesis.marks());
                send(new AvatarMessage.Segment(currentTurnSeq, index, seg.cleanText(),
                                               true, visemes, timedCues));
                sendAudioFrame(synthesis.audioData(), currentTurnSeq, index, true);
            }
        } catch (Exception e) {
            send(new AvatarMessage.Segment(currentTurnSeq, index, seg.cleanText(),
                                           false, List.of(), List.of()));
            send(new AvatarMessage.Error("TTS failed for segment " + index
                                         + ": " + e.getMessage()));
        }
    }

    private void sendAudioFrame(byte[] audioData, int turnSeq, int segmentIndex, boolean isFinal) {
        var buf = java.nio.ByteBuffer.allocate(8 + audioData.length)
                                     .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);
        buf.put((byte) (isFinal ? 1 : 0));
        buf.putShort((short) segmentIndex);
        buf.putInt(turnSeq);
        buf.put(audioData);
        binarySink.accept(buf.array());
    }


    private AssembledPrompt assemblePrompt(UserTurn turn) {
        AssembledPrompt prompt = assembler.assemble(turn.cleanText(), List.copyOf(history));
        if (turn.llmModel() != null) {
            prompt = new AssembledPrompt(prompt.systemPrompt(), prompt.userMessage(),
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

    private static List<TextMark> buildTextMarks(List<Cue> cues, int textLength) {
        var marks = new ArrayList<TextMark>();
        for (int i = 0; i < cues.size(); i++) {
            int offset = cueCharOffset(cues.get(i));
            if (offset >= 0) {
                marks.add(new TextMark("cue-" + i, Math.min(offset, textLength)));
            }
        }
        return marks;
    }

    static List<Cue> estimateMarkTiming(List<Cue> cues, int textLength, long estimatedDurationMs) {
        if (cues.isEmpty() || textLength == 0) return cues;
        var result = new ArrayList<Cue>(cues.size());
        for (var cue : cues) {
            int charOffset = cueCharOffset(cue);
            if (charOffset >= 0) {
                long offsetMs = (long) charOffset * estimatedDurationMs / textLength;
                result.add(withOffsetMs(cue, offsetMs));
            } else {
                result.add(cue);
            }
        }
        return result;
    }

    private static List<Cue> applyMarkTiming(List<Cue> cues,
                                              List<io.casehub.connectors.voice.model.MarkTiming> marks) {
        if (marks.isEmpty()) return cues;
        var timingMap = new java.util.HashMap<String, Long>();
        for (var mt : marks) timingMap.put(mt.id(), mt.offsetMs());
        var result = new ArrayList<Cue>(cues.size());
        for (int i = 0; i < cues.size(); i++) {
            Long timing = timingMap.get("cue-" + i);
            if (timing != null) {
                result.add(withOffsetMs(cues.get(i), timing));
            } else {
                result.add(cues.get(i));
            }
        }
        return result;
    }

    private static Cue withOffsetMs(Cue cue, long offsetMs) {
        return switch (cue) {
            case Cue.GestureCue g -> new Cue.GestureCue(g.tag(), offsetMs, g.charOffset());
            case Cue.MoodCue m -> new Cue.MoodCue(m.mood(), offsetMs, m.charOffset());
        };
    }

    private static int cueCharOffset(Cue cue) {
        return switch (cue) {
            case Cue.GestureCue g -> g.charOffset();
            case Cue.MoodCue m -> m.charOffset();
        };
    }
}

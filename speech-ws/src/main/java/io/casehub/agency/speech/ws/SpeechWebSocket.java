package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.CorrectionHooks;
import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.eidos.api.AssembledPrompt;
import io.casehub.agency.speech.ws.protocol.AvatarMessage;
import io.casehub.agency.speech.ws.protocol.MessageCodec;
import io.casehub.agency.core.yaml.compiler.CompiledSpeechPipeline;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

import java.nio.ByteOrder;

@WebSocket(path = "/ws/avatar")
public class SpeechWebSocket {

    @Inject
    StreamingSpeechToText sttService;

    @Inject
    TextToSpeech ttsService;

    @Inject
    CompiledSpeechPipeline pipeline;

    @Inject
    AvatarConfig avatarConfig;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.platform.agent.AgentProvider> agentProvider;


    @Inject
    jakarta.enterprise.inject.Instance<TtsModelRegistry> ttsRegistry;
    @Inject
    jakarta.enterprise.inject.Instance<CorrectionHooks>  correctionHooks;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.connectors.voice.spi.SpeakerEmbeddingExtractor> embeddingExtractor;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.connectors.voice.spi.SpeakerRegistry> speakerRegistryInstance;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.agency.speech.AvatarCognition> avatarCognition;

    private final java.util.concurrent.ConcurrentHashMap<String, SpeechSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Thread> proactiveThreads = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, SilenceAttentionPolicy> attentionPolicies = new java.util.concurrent.ConcurrentHashMap<>();

    private static final System.Logger LOG = System.getLogger("speech-ws");

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
      try {
        String connId = connection.id();
        LOG.log(System.Logger.Level.INFO, "WebSocket onOpen connId={0}", connId);
        var assembler = new DefaultPromptAssembler(
                avatarConfig.systemPrompt().orElse(null));

        java.util.function.Function<AssembledPrompt, String> generator    = null;
        SpeechSession.StreamingResponseGenerator             streamingGen = null;
        try { if (agentProvider.isResolvable()) {
            var agent = agentProvider.get();
            generator = prompt -> {
                var config = prompt.model() != null
                             ? io.casehub.platform.agent.AgentSessionConfig.of(
                        prompt.systemPrompt(), prompt.userMessage(), prompt.model())
                             : io.casehub.platform.agent.AgentSessionConfig.of(
                        prompt.systemPrompt(), prompt.userMessage());
                return agent.invoke(config)
                            .filter(e -> e instanceof io.casehub.platform.agent.AgentEvent.TextDelta)
                            .map(e -> ((io.casehub.platform.agent.AgentEvent.TextDelta) e).text())
                            .collect().asList()
                            .map(parts -> String.join("", parts))
                            .await().atMost(java.time.Duration.ofSeconds(60));
            };

            streamingGen = (prompt, onSentence) -> {
                var config = prompt.model() != null
                             ? io.casehub.platform.agent.AgentSessionConfig.of(
                        prompt.systemPrompt(), prompt.userMessage(), prompt.model())
                             : io.casehub.platform.agent.AgentSessionConfig.of(
                        prompt.systemPrompt(), prompt.userMessage());
                var buffer   = new StringBuilder();
                var fullText = new StringBuilder();

                agent.invoke(config)
                     .filter(e -> e instanceof io.casehub.platform.agent.AgentEvent.TextDelta)
                     .map(e -> ((io.casehub.platform.agent.AgentEvent.TextDelta) e).text())
                     .onItem().invoke(delta -> {
                         fullText.append(delta);
                         buffer.append(delta);
                         int boundary = findSentenceBoundary(buffer);
                         while (boundary > 0) {
                             String sentence = buffer.substring(0, boundary).trim();
                             buffer.delete(0, boundary);
                             if (!sentence.isEmpty()) {
                                 onSentence.accept(sentence);
                             }
                             boundary = findSentenceBoundary(buffer);
                         }
                     })
                     .collect().last()
                     .await().atMost(java.time.Duration.ofSeconds(120));

                if (!buffer.isEmpty()) {
                    String remaining = buffer.toString().trim();
                    if (!remaining.isEmpty()) {
                        onSentence.accept(remaining);
                    }
                }
                return fullText.toString();
            };
        } } catch (Exception e) {
            System.getLogger("speech-ws").log(System.Logger.Level.WARNING,
                    "LLM provider unavailable — text responses disabled: " + e.getMessage());
        }

        CorrectionHooks hooks = correctionHooks.isResolvable() ? correctionHooks.get() : null;

        var session = new SpeechSession(
                sttService, ttsService, pipeline,
                generator,
                streamingGen,
                assembler,
                text -> connection.sendTextAndAwait(text),
                data -> connection.sendBinaryAndAwait(data),
                ttsRegistry.isResolvable() ? ttsRegistry.get().models() : java.util.Map.of(),
                hooks != null ? hooks.corrector() : null,
                hooks != null ? hooks.onResponse() : null,
                hooks != null ? hooks.vocabularyHintSupplier() : null);

        if (embeddingExtractor.isResolvable() && speakerRegistryInstance.isResolvable()) {
            session.withSpeakerServices(embeddingExtractor.get(), speakerRegistryInstance.get());
        }

        if (avatarCognition.isResolvable()
                && avatarConfig.agentId().isPresent()
                && avatarConfig.tenantId().isPresent()) {
            var cog = avatarCognition.get();
            var agentId = avatarConfig.agentId().get();
            var tenantId = avatarConfig.tenantId().get();
            var wrappedAssembler = cog.wrapAssembler(assembler, agentId, tenantId,
                    session::currentSubjectId);
            session = new SpeechSession(
                    sttService, ttsService, pipeline,
                    generator, streamingGen, wrappedAssembler,
                    text -> connection.sendTextAndAwait(text),
                    data -> connection.sendBinaryAndAwait(data),
                    ttsRegistry.isResolvable() ? ttsRegistry.get().models() : java.util.Map.of(),
                    hooks != null ? hooks.corrector() : null,
                    hooks != null ? hooks.onResponse() : null,
                    hooks != null ? hooks.vocabularyHintSupplier() : null);
            if (embeddingExtractor.isResolvable() && speakerRegistryInstance.isResolvable()) {
                session.withSpeakerServices(embeddingExtractor.get(), speakerRegistryInstance.get());
            }
            session.withAvatarCognition(cog, agentId, tenantId);
            cog.initialize(agentId, tenantId);
            startProactiveTick(connId, session, cog, agentId, tenantId,
                               avatarConfig.proactiveTickIntervalSeconds(),
                               text -> connection.sendTextAndAwait(text));
        } else if (avatarConfig.attentionEnabled()) {
            var policy = new SilenceAttentionPolicy(
                    session.floorState(),
                    text -> connection.sendTextAndAwait(text),
                    avatarConfig.attentionSubtleSeconds(),
                    avatarConfig.attentionStrongSeconds());
            attentionPolicies.put(connId, policy);
            policy.start();
        }
        session.setMinPlaybackRate(avatarConfig.minPlaybackRate());
        sessions.put(connId, session);
        LOG.log(System.Logger.Level.INFO, "WebSocket session initialized connId={0}", connId);
      } catch (Exception e) {
        LOG.log(System.Logger.Level.ERROR, "WebSocket onOpen failed: " + e.getMessage(), e);
      }
    }

    private void startProactiveTick(String connId, SpeechSession session,
                                     io.casehub.agency.speech.AvatarCognition cog,
                                     String agentId, String tenantId, int intervalSeconds,
                                     java.util.function.Consumer<String> textSink) {
        var moodMapper = cog.providesAffect() ? new MoodMapper() : null;
        var thread = Thread.ofVirtual().name("avatar-proactive").start(() -> {
            while (!session.isClosed()) {
                try {
                    Thread.sleep(java.time.Duration.ofSeconds(intervalSeconds));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (session.isClosed()) return;
                try {
                    cog.tick(agentId, tenantId, java.util.Set.of(session.currentSubjectId()));
                    if (moodMapper != null) {
                        var affect = cog.currentAffect(agentId, tenantId);
                        if (affect != null) {
                            String prev = moodMapper.current();
                            String mapped = moodMapper.map(affect, System.currentTimeMillis());
                            if (mapped != null && !mapped.equals(prev)) {
                                textSink.accept(
                                        MessageCodec.encode(new AvatarMessage.Mood(mapped)));
                            }
                        }
                    }
                    if (!session.shouldSuppressProactive()) {
                        String channelContext = "Channel: WebSocket speech session\nTurn count: "
                                + session.historySize() + "\nSpeaker: " + session.currentSubjectId();
                        String content = cog.evaluateProactive(agentId, tenantId, channelContext);
                        if (content != null) {
                            session.handleProactiveSpeech(content);
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
        proactiveThreads.put(connId, thread);
    }

    @OnTextMessage
    public void onText(WebSocketConnection connection, String message) {
        var session = sessions.get(connection.id());
        if (session == null || session.isClosed()) {
            LOG.log(System.Logger.Level.WARNING, "onText with no active session connId={0}", connection.id());
            return;
        }
        AvatarMessage msg = MessageCodec.decodeClient(message);
        switch (msg) {
            case AvatarMessage.Start s -> session.handleStart(s);
            case AvatarMessage.Stop s -> session.handleStop();
            case AvatarMessage.Text t -> session.handleText(t.text(), t.llmModel(), t.ttsModel());
            case AvatarMessage.SpeakerIdentify si -> session.handleSpeakerIdentify(si.name());
            case AvatarMessage.PlaybackComplete pc -> session.handlePlaybackComplete(pc.turnSeq());
            case AvatarMessage.UserActivity ua -> session.handleUserActivity();
            default -> {}
        }
    }

    @OnBinaryMessage
    public void onBinary(WebSocketConnection connection, byte[] data) {
        var session = sessions.get(connection.id());
        if (session == null) return;
        var     floatBuf = java.nio.ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] samples  = new float[floatBuf.remaining()];
        floatBuf.get(samples);
        session.handleAudio(samples);
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        String connId = connection.id();
        LOG.log(System.Logger.Level.INFO, "WebSocket onClose connId={0}", connId);
        var session = sessions.remove(connId);
        if (session != null) {
            session.close();
        }
        var thread = proactiveThreads.remove(connId);
        if (thread != null) {
            thread.interrupt();
        }
        var policy = attentionPolicies.remove(connId);
        if (policy != null) {
            policy.stop();
        }
    }

    private static int findSentenceBoundary(StringBuilder buffer) {
        for (int i = 0; i < buffer.length() - 1; i++) {
            char c = buffer.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(buffer.charAt(i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }

}

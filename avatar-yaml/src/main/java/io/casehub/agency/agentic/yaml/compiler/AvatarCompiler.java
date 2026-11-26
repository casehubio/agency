package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.casehub.agency.core.yaml.spec.CleanupConfigSpec;
import io.casehub.agency.core.yaml.spec.LipSyncMode;
import io.casehub.agency.core.yaml.spec.SttSpec;
import io.casehub.agency.core.yaml.spec.TtsEngineSpec;
import io.casehub.agency.core.yaml.spec.TtsSpec;
import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.PhonemeAligner;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextFilter;
import io.casehub.connectors.voice.spi.TextToSpeech;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class AvatarCompiler {

    private static final System.Logger LOG = System.getLogger("avatar-compiler");

    public CompiledAvatar compile(AvatarDefinition definition) {
        TtsModelRegistry      ttsRegistry   = null;
        TextToSpeech          defaultTts    = null;
        StreamingSpeechToText stt           = null;
        CleanupConfig         cleanupConfig = null;

        if (definition.tts() != null) {
            var models = compileTts(definition.tts());
            ttsRegistry = new TtsModelRegistry(models);
            defaultTts  = models.isEmpty() ? null : models.values().iterator().next();
        }
        if (definition.stt() != null) {
            stt = compileStt(definition.stt());
        }
        if (definition.cleanup() != null) {
            cleanupConfig = compileCleanup(definition.cleanup());
        }

        CompiledSpeechPipeline pipeline;
        if (definition.pipeline() != null) {
            var filterRegistry   = createDefaultFilterRegistry();
            var pipelineCompiler = new SpeechPipelineCompiler();
            FilterChain postSttFallback = cleanupConfig != null
                                          ? FilterChain.of(cleanupConfig) : FilterChain.EMPTY;
            pipeline = pipelineCompiler.compileWithFallback(
                    definition.pipeline(), filterRegistry, postSttFallback);
        } else if (cleanupConfig != null) {
            pipeline = new CompiledSpeechPipeline(
                    FilterChain.of(cleanupConfig), FilterChain.EMPTY, FilterChain.EMPTY);
        } else {
            pipeline = CompiledSpeechPipeline.EMPTY;
        }

        return new CompiledAvatar(ttsRegistry, defaultTts, stt, cleanupConfig, pipeline);
    }


    static TextFilterRegistry createDefaultFilterRegistry() {
        var registry = new TextFilterRegistry();
        registry.register("filler-removal",
                          config -> new io.casehub.connectors.voice.sherpa.FillerRemovalFilter());
        registry.register("casing",
                          config -> new io.casehub.connectors.voice.sherpa.CasingFilter());
        registry.register("punctuation", config -> {
            try {
                return io.casehub.connectors.voice.sherpa.PunctuationFilter.withDefaults();
            } catch (Exception e) {
                throw new RuntimeException("PunctuationFilter unavailable: " + e.getMessage(), e);
            }
        });
        registry.register("grammar", config -> {
            try {
                return io.casehub.connectors.voice.sherpa.GectorFilter.withDefaults();
            } catch (Exception e) {
                throw new RuntimeException("GECToR unavailable: " + e.getMessage(), e);
            }
        });
        return registry;
    }

    private Map<String, TextToSpeech> compileTts(TtsSpec spec) {
        var models = new LinkedHashMap<String, TextToSpeech>();
        PhonemeAligner sharedAligner = null;
        try {
            sharedAligner = io.casehub.connectors.voice.sherpa.EspeakPhonemeAligner.withDefaults();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "EspeakPhonemeAligner unavailable — lip-sync disabled: " + e.getMessage());
        }
        for (var engine : spec.engines()) {
            models.putAll(compileEngine(engine, sharedAligner));
        }
        return models;
    }

    private Map<String, TextToSpeech> compileEngine(TtsEngineSpec engine, PhonemeAligner aligner) {
        return switch (engine.type()) {
            case "vits" -> compileVits(engine);
            case "sherpa" -> compileSherpa(engine, aligner);
            case "kokoro" -> compileKokoro(engine, aligner);
            case "qwen3" -> compileQwen3(engine);
            default -> throw new IllegalArgumentException("Unknown TTS engine type: " + engine.type());
        };
    }

    private Map<String, TextToSpeech> compileVits(TtsEngineSpec engine) {
        var result = new LinkedHashMap<String, TextToSpeech>();
        for (var voice : engine.voices()) {
            try {
                var model = voice.model();
                var tts = model != null
                        ? io.casehub.connectors.voice.sherpa.VitsTextToSpeech.withDefaults(model)
                        : io.casehub.connectors.voice.sherpa.VitsTextToSpeech.withDefaults();
                result.put(voice.name(), tts);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "VITS voice ''{0}'' unavailable: {1}", voice.name(), e.getMessage());
            }
        }
        return result;
    }

    private Map<String, TextToSpeech> compileSherpa(TtsEngineSpec engine, PhonemeAligner aligner) {
        var result = new LinkedHashMap<String, TextToSpeech>();
        boolean wrapLipSync = shouldWrapLipSync(engine.lipSync(), aligner);
        for (var voice : engine.voices()) {
            var model = voice.model();
            TextToSpeech tts = model != null
                    ? io.casehub.connectors.voice.sherpa.SherpaOnnxTextToSpeech.withDefaults(model)
                    : io.casehub.connectors.voice.sherpa.SherpaOnnxTextToSpeech.withDefaults();
            if (wrapLipSync && aligner != null) {
                tts = io.casehub.connectors.voice.spi.LipSyncEnricher.wrap(tts, aligner);
            }
            result.put("sherpa:" + voice.name(), tts);
        }
        return result;
    }

    private Map<String, TextToSpeech> compileKokoro(TtsEngineSpec engine, PhonemeAligner aligner) {
        var result = new LinkedHashMap<String, TextToSpeech>();
        var kokoro = io.casehub.connectors.voice.sherpa.KokoroTextToSpeech.withDefaults(0);
        boolean wrapLipSync = shouldWrapLipSync(engine.lipSync(), aligner);
        for (var voice : engine.voices()) {
            int voiceId = resolveKokoroVoiceId(voice.name());
            TextToSpeech tts = kokoro.forVoice(voiceId);
            if (wrapLipSync && aligner != null) {
                tts = io.casehub.connectors.voice.spi.LipSyncEnricher.wrap(tts, aligner);
            }
            result.put("kokoro:" + voice.name(), tts);
        }
        return result;
    }

    private Map<String, TextToSpeech> compileQwen3(TtsEngineSpec engine) {
        var result = new LinkedHashMap<String, TextToSpeech>();
        for (var voice : engine.voices()) {
            try {
                result.put("qwen3:" + voice.name(),
                        io.casehub.connectors.voice.sherpa.Qwen3TextToSpeech.withDefaults(voice.name()));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Qwen3-TTS voice ''{0}'' unavailable: {1}", voice.name(), e.getMessage());
            }
        }
        if (engine.cloneDiscovery()) {
            try {
                var pool = io.casehub.connectors.voice.sherpa.Qwen3VoicePool.createProvisioned(
                        io.casehub.connectors.voice.sherpa.Qwen3VoicePoolConfig.defaults());
                for (String voice : pool.availableVoices()) {
                    result.put("qwen3:clone:" + voice, pool.forVoice(voice));
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Qwen3-TTS voice pool unavailable: " + e.getMessage());
            }
        }
        return result;
    }

    private boolean shouldWrapLipSync(LipSyncMode mode, PhonemeAligner aligner) {
        if (mode == null || mode == LipSyncMode.AUTO) return aligner != null;
        if (mode == LipSyncMode.ENABLED) {
            if (aligner == null) throw new IllegalStateException(
                    "lip-sync: enabled but no PhonemeAligner available");
            return true;
        }
        return false;
    }

    private StreamingSpeechToText compileStt(SttSpec spec) {
        return switch (spec.type()) {
            case "whisper" -> io.casehub.connectors.voice.sherpa.WhisperSpeechToText.withDefaults();
            case "zipformer" -> io.casehub.connectors.voice.sherpa.SherpaOnnxStreamingSpeechToText.withDefaults();
            case "whisper-fallback" -> compileWhisperFallback();
            default -> throw new IllegalArgumentException("Unknown STT type: " + spec.type());
        };
    }

    private StreamingSpeechToText compileWhisperFallback() {
        try {
            if (io.casehub.connectors.voice.sherpa.WhisperLibrary.isAvailable()) {
                return io.casehub.connectors.voice.sherpa.WhisperSpeechToText.withDefaults();
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Whisper init failed, falling back: " + e.getMessage());
        }
        return io.casehub.connectors.voice.sherpa.SherpaOnnxStreamingSpeechToText.withDefaults();
    }

    static CleanupConfig compileCleanup(CleanupConfigSpec spec) {
        var filters = new ArrayList<TextFilter>();
        filters.add(new io.casehub.connectors.voice.sherpa.FillerRemovalFilter());
        filters.add(new io.casehub.connectors.voice.sherpa.CasingFilter());
        if (spec.maxDestructiveness() >= 2) {
            try {
                filters.add(io.casehub.connectors.voice.sherpa.PunctuationFilter.withDefaults());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "PunctuationFilter unavailable: " + e.getMessage());
            }
        }
        if (spec.maxDestructiveness() >= 3) {
            try {
                filters.add(io.casehub.connectors.voice.sherpa.GectorFilter.withDefaults());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "GECToR unavailable: " + e.getMessage());
            }
        }
        return CleanupConfig.of(filters.toArray(TextFilter[]::new));
    }

    private static int resolveKokoroVoiceId(String name) {
        return switch (name) {
            case "af_alloy" -> 0; case "af_aoede" -> 1; case "af_bella" -> 2;
            case "af_heart" -> 3; case "af_jessica" -> 4; case "af_kore" -> 5;
            case "af_nicole" -> 6; case "af_nova" -> 7; case "af_river" -> 8;
            case "af_sarah" -> 9; case "af_sky" -> 10; case "am_adam" -> 11;
            case "am_echo" -> 12; case "am_eric" -> 13; case "am_fenrir" -> 14;
            case "am_liam" -> 15; case "am_michael" -> 16; case "am_onyx" -> 17;
            case "am_puck" -> 18; case "am_santa" -> 19; case "bf_alice" -> 20;
            case "bf_emma" -> 21; case "bf_isabella" -> 22; case "bf_lily" -> 23;
            case "bm_daniel" -> 24; case "bm_fable" -> 25; case "bm_george" -> 26;
            case "bm_lewis" -> 27; case "ef_dora" -> 28; case "em_alex" -> 29;
            case "ff_siwis" -> 30; case "hf_alpha" -> 31; case "hf_beta" -> 32;
            case "hm_omega" -> 33; case "hm_psi" -> 34; case "if_sara" -> 35;
            case "im_nicola" -> 36; case "jf_alpha" -> 37; case "jf_gongitsune" -> 38;
            case "jf_nezumi" -> 39; case "jf_tebukuro" -> 40; case "jm_kumo" -> 41;
            case "pf_dora" -> 42; case "pm_alex" -> 43; case "pm_santa" -> 44;
            case "zf_xiaobei" -> 45; case "zf_xiaoni" -> 46; case "zf_xiaoxiao" -> 47;
            case "zf_xiaoyi" -> 48; case "zm_yunjian" -> 49; case "zm_yunxi" -> 50;
            case "zm_yunxia" -> 51; case "zm_yunyang" -> 52;
            default -> throw new IllegalArgumentException("Unknown Kokoro voice: " + name);
        };
    }
}

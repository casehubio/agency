## D1: Bridge architecture — compiler/registry pattern

**Choice:** Compiler/registry in agentic-yaml — per-engine-type registries translate `TtsEngineSpec` to `Map<String, TextToSpeech>`. An `AvatarCompiler` iterates `tts.engines`, calls the registry for each type, and merges results into a single `TtsModelRegistry` (in speech-api; see D9). Registries live in agentic-yaml alongside existing `KokoroConfigRegistry`, `SherpaConfigRegistry`, `Audio8ConfigRegistry`, using the existing `provided`-scope dependency on `casehub-connectors-voice-sherpa` (JDK 22+ profile).
**Alternatives:**
- SPI registry — define factory interfaces in speech-api, CDI-discovered implementations per engine type. Extensible but premature: all engine implementations are in `casehub-connectors-voice-sherpa` and there are no cross-module providers. The SPI indirection adds factory interfaces, CDI discovery, and registration complexity without an immediate consumer. If a cross-module engine provider appears (cloud TTS, third-party), SPIs can be introduced at that point.
- Direct coupling module — single module depends on both agentic-yaml and speech-sherpa. Same result as the compiler approach but adds an unnecessary module.
**Rationale:** The compiler/registry pattern matches the design spec's `TtsEngineRegistry.resolve(TtsEngineSpec) → Map<String, TextToSpeech>` and extends the existing config registries already in agentic-yaml. Adding a new engine type means one new registry method in the compiler — mechanical and testable. Avoids the circular dependency that the SPI approach creates (R1-02).
**Trade-offs:** Less open-for-extension than SPI — adding an engine type from a different module requires editing the compiler. Acceptable when all implementations share the same FFM infrastructure in one module.
**Sources:** `SpeechProducers.java`, `KokoroConfigRegistry.java`, `SherpaConfigRegistry.java`, `Audio8ConfigRegistry.java`, `2026-11-19-avatar-mosaic-component-design.md` §Registry pattern
**Exploration:** quick
**Status:** revised (R1-02, R1-03 — SPI dropped in favour of compiler/registry to eliminate circular dependency and unnecessary abstraction)
**Note:** On JDK < 22, the speech engine registries are excluded from compilation by the existing Maven profile. Apps on JDK < 22 get no YAML-driven speech beans and must provide their own CDI producers or rely on @DefaultBean defaults.

## D2: Registries and spec records stay in agentic-yaml

**Choice:** Engine registries and spec records (`TtsEngineSpec`, `SttSpec`, `TtsVoiceSpec`, `CleanupConfigSpec`, `TtsSpec`) remain in agentic-yaml. No factory SPIs in speech-api. speech-api stays focused on runtime pipeline SPIs (`AvatarCognition`, `SpeechPromptAssembler`, etc.).
**Alternatives:**
- Factory SPIs in speech-api — requires speech-api to depend on agentic-yaml for spec records, creating a circular dependency (`agentic-yaml → speech-api` already exists as `provided`). Maven cannot resolve this.
- Extract spec records to a shared `speech-spec` module — eliminates the cycle but adds a module for small data records. The records are YAML deserialization targets tightly coupled to the avatar YAML surface.
- Factory interfaces use engine-agnostic parameters (`String type, Map<String, Object> config`) — eliminates the dependency on spec records but loses type safety.
**Rationale:** The spec records are YAML deserialization targets for the avatar configuration surface. They belong with the YAML infrastructure in agentic-yaml. The registries bridge spec records to engine implementations using existing `provided`-scope dependencies. No cross-module SPI boundary is needed.
**Trade-offs:** speech-api has no knowledge of YAML spec shapes. Consumers that want to programmatically construct engine specs must depend on agentic-yaml.
**Sources:** `agentic-yaml/pom.xml` (dependency graph), `speech-api/pom.xml` (no agentic-yaml dependency)
**Exploration:** quick
**Depends on:** D1 (compiler/registry pattern)
**Status:** revised (R1-02 — eliminated circular dependency by keeping registries in agentic-yaml instead of adding factory SPIs to speech-api)

## D3: Bean assembly via deployment recorder in agentic-yaml

**Choice:** Speech bean assembly moves to agentic-yaml's existing deployment/recorder pattern. `AgenticYamlProcessor` discovers `avatar.yaml`, the `AvatarCompiler` translates the definition to runtime bean values, and `AgenticRecorder` registers them as synthetic CDI beans at `RUNTIME_INIT`. Any app with `agentic-yaml` + `avatar.yaml` on the classpath gets speech beans — no dependency on speech-ws required.
**Alternatives:**
- Producer in speech-ws — couples bean assembly to WebSocket transport. Non-WebSocket consumers (REST endpoints, batch/CLI, future transport modules) would need to depend on speech-ws or duplicate assembly. The `AvatarConfigResource` REST endpoint already exists in agentic-yaml, demonstrating a non-WebSocket consumer of avatar config.
- New avatar-runtime module — cleanest separation but unnecessary: the deployment/recorder pattern is already established in agentic-yaml-deployment for `AvatarConfigBean`.
**Rationale:** The deployment/recorder pattern is transport-agnostic and follows the established `AvatarConfigBean` precedent. The recorder runs at startup (RUNTIME_INIT), so FFM native calls work normally. The original dismissal of this approach as "complex for FFM runtime calls" was incorrect — recorders execute at runtime, not build time.
**Trade-offs:** More code in agentic-yaml-deployment/recorder. The existing `AvatarConfigBuildItem` pattern makes this mechanical.
**Sources:** `AgenticYamlProcessor.java`, `AgenticRecorder.java`, `AvatarConfigBuildItem.java`, `AvatarConfigBean.java`
**Exploration:** quick
**Depends on:** D1 (compiler/registry)
**Status:** revised (R1-04 — moved from speech-ws to agentic-yaml deployment/recorder for transport independence)

## D4: Infrastructure beans — split placement by visibility

**Choice:** Infrastructure beans are split across two assembly points based on type visibility:

**connectors-voice-sherpa @DefaultBean:** `SpeakerDiarizationService`, `SpeakerEmbeddingExtractor`, `SpeakerRegistry`, `VoiceprintStore`, `TranscriptCorrector`, `ConversationVocabulary`. These types and their implementations are self-contained within connectors-voice-sherpa. Any app with connectors-voice-sherpa on classpath gets sensible defaults.

**agentic-yaml deployment recorder:** `CorrectionHooks` (in speech-api; see D9). Assembled from `TranscriptCorrector` + `ConversationVocabulary` by the recorder when avatar.yaml configures speech (consistent with D3). `CorrectionHooks` cannot have a @DefaultBean in connectors-voice-sherpa because connectors-voice-sherpa cannot see the `CorrectionHooks` type (dependency flows agency → connectors, never connectors → agency). Without avatar.yaml, `CorrectionHooks` is absent — `SpeechWebSocket` handles this gracefully via `Instance<CorrectionHooks>.isResolvable()`.

**Alternatives:**
- All @DefaultBean in connectors-voice-sherpa — impossible for CorrectionHooks (cross-repo visibility).
- @DefaultBean for CorrectionHooks in speech-ws — speech-ws cannot see `TranscriptCorrector`/`ConversationVocabulary` (they're in connectors-voice-sherpa; speech-ws only sees connectors-voice-spi transitively).
- Extend YAML spec — add diarization, speaker, correction sections to AvatarDefinition. Full control but rarely varies.
- Keep in SpeechProducers — only migrate TTS/STT/cleanup to YAML. Leaves hardcoded producers in demo app.
**Rationale:** The split follows type visibility boundaries. Infrastructure beans whose types and implementations are self-contained in connectors get @DefaultBean there. CorrectionHooks crosses the connectors/agency boundary (type in speech-api, components in connectors-voice-sherpa), so it's assembled by the deployment recorder which sees both sides via provided-scope dependencies.
**Trade-offs:** CorrectionHooks is unavailable without avatar.yaml unless the app provides its own CDI producer (tier 3). This is acceptable — the existing `SpeechWebSocket` already handles absence.
**Sources:** `SpeechProducers.java` lines 124-319, `CorrectionHooks.java`, `SpeechWebSocket.java` line 117
**Exploration:** quick
**Depends on:** D1 (compiler/registry), D3 (deployment recorder), D9 (CorrectionHooks relocation)
**Status:** revised (R1-04, R2-02 — split bean placement by type visibility; CorrectionHooks assembly via recorder)

## D5: Lip-sync wrapping controlled by YAML with auto-detect default

**Choice:** `TtsEngineSpec` gains an optional `lipSync` field: `auto` (default) | `enabled` | `disabled`. The compiler inspects engine capabilities under `auto`: if the engine returns empty phonemes and a `PhonemeAligner` is available, `LipSyncEnricher` wrapping is applied. `enabled` forces wrapping (error if no aligner), `disabled` skips it.
**Alternatives:**
- Factory-internal auto-wrap — zero configuration but no YAML escape hatch. Contradicts D6's three-tier model: an app that needs to disable lip-sync for performance or quality reasons cannot do so via YAML, only by overriding the entire engine construction.
- Always require explicit YAML — verbose for the common case where auto-detect is correct.
**Rationale:** Lip-sync varies per deployment (performance-constrained environments, testing, engines where post-hoc aligning degrades quality). Making it configurable in the YAML layer is consistent with D6's three-tier model: auto-detect as default, YAML override when needed.
**Trade-offs:** One more optional field in TtsEngineSpec. Minimal complexity.
**Sources:** `SpeechProducers.java` lines 29-34, 44-48, `LipSyncEnricher` in connectors-voice-spi
**Exploration:** quick
**Depends on:** D1 (compiler/registry), D6 (three-tier model)
**Status:** revised (R1-05 — added YAML control to resolve contradiction with D6's three-tier precedence model)

## D6: Three-tier precedence — defaults, YAML, CDI override

**Choice:** Three-tier bean resolution: (1) connectors-voice-sherpa `@DefaultBean`s for zero-config sensible defaults, (2) `avatar.yaml` for declarative customisation — only needed when diverging from defaults, (3) app-level CDI `@Produces` for programmatic control. Higher tier displaces lower. YAML is the customisation layer, not boilerplate — if defaults work, skip it entirely.

**Conditional production:** Each YAML section independently produces its corresponding bean. When `avatar.yaml` exists but a section is absent (e.g., `tts:` is null), no YAML-tier bean is produced for that type, and the @DefaultBean provides the fallback. The deployment build step checks `avatarConfig.definition().tts() != null` before recording TTS beans, `stt != null` before recording STT beans, etc. This makes tiers 1 and 2 composable at the section level — an app can customise TTS via YAML while keeping default STT.

**Alternatives:**
- YAML-only — no default beans, YAML always required. Boilerplate for the common case.
- CDI-only — keep current approach. Defeats the purpose of this issue.
- Two-tier (YAML + CDI) — no zero-config path. Apps must write either YAML or producers.
- Producer always fires — when YAML section is null, producer falls back to defaults internally. Collapses tier 1 and 2 into one code path, defeating the three-tier model.
**Rationale:** Convention over configuration. Most deployments want "working avatar with defaults." YAML adds value only when you need specific voices, non-default parameters, or custom cleanup rules. CDI override is the escape hatch for things YAML can't express (Vertex AI wiring, custom factories).
**Trade-offs:** Three precedence tiers to understand; @DefaultBean priority rules must be clear in docs.
**Sources:** `SpeechProducers.java`, Quarkus `@DefaultBean` semantics
**Exploration:** quick
**Depends on:** D3 (deployment recorder in agentic-yaml), D4 (default beans in connectors-voice-sherpa)
**Status:** revised (R1-06 — specified conditional production semantics for partial YAML sections)

## D7: AvatarConfig (properties) vs AvatarConfigBean (YAML) — scope separation

**Choice:** `AvatarConfig` (SmallRye `@ConfigMapping`) retains platform/runtime fields: `systemPrompt`, `agentId`, `tenantId`, `proactiveTickIntervalSeconds`, `attentionEnabled`, `attentionSubtleSeconds`, `attentionStrongSeconds`, `maxDestructiveness`. `AvatarDefinition` (YAML) owns physical-layer fields: `sampleRate`, `minPlaybackRate`, `speed`, `ttsVoice`, `cameraView`, `model`, `tts`, `stt`, `cleanup`. `AvatarConfig.sampleRate()` and `AvatarConfig.minPlaybackRate()` are deprecated — YAML is the source of truth for these fields. `SpeechWebSocket` reads physical-layer values from `AvatarConfigBean.definition()` and runtime/platform values from `AvatarConfig`.
**Alternatives:**
- Merge into one — combine both into `AvatarDefinition` YAML. Mixes platform concerns (agent identity, tenancy) with physical-layer concerns (3D model, voice pipeline). The design spec explicitly separates these: "The YAML configures only the physical rendering and speech pipeline. The agent brings the mind."
- Keep both with overlapping fields — apps face confusing dual-config behavior where the same field can be set in two places with unclear precedence.
- Deprecate AvatarConfig entirely — loses the SmallRye `application.properties` path for runtime tuning (attention thresholds, proactive tick interval). These values benefit from hot-reload and environment-variable injection, which SmallRye provides natively.
**Rationale:** The design spec's separation of concerns (avatar = body, eidos = mind) maps cleanly to two config surfaces. Physical-layer config is declarative and static (YAML). Runtime/platform config is environment-dependent and hot-reloadable (properties).
**Trade-offs:** Two injection points in `SpeechWebSocket`. Clear ownership eliminates ambiguity.
**Sources:** `AvatarConfig.java`, `AvatarConfigBean.java`, `AvatarDefinition.java`, `2026-11-19-avatar-mosaic-component-design.md` §Separation of concerns
**Exploration:** quick (surfaced by reviewer R1-07)
**Status:** captured

## D8: Voice registry mapping — TtsModelRegistry population from YAML

**Choice:** Each engine type's registry method produces a `Map<String, TextToSpeech>` from its `TtsEngineSpec`. Voice name keys follow `{enginePrefix}:{voiceName}` format (e.g., `kokoro:af_heart`, `qwen3:ryan`). VITS voices use bare names (`lessac-medium`). The compiler merges all engine maps into one `TtsModelRegistry`.

**Per-engine-type semantics:**
- **VITS/SherpaOnnx:** Each `TtsVoiceSpec` creates a separate engine instance. `voice.model()` selects the ONNX model; null means engine default.
- **Kokoro:** One shared `KokoroTextToSpeech.withDefaults(0)` engine per `TtsEngineSpec`. Each `TtsVoiceSpec` maps to `kokoro.forVoice(voiceIndex)`. Voice index is resolved from the voice name.
- **Qwen3 static voices:** Each `TtsVoiceSpec` creates a `Qwen3TextToSpeech.withDefaults(voiceName)`.
- **Qwen3 cloned voices:** `clonedVoices` list triggers `Qwen3VoicePool.createProvisioned(config)`. Each cloned voice name maps to `pool.forVoice(name)`. Keyed as `qwen3:clone:{name}`. Discovery is from `.wav` files on disk — YAML enumerates the expected voice names, the pool validates availability at startup.

**TtsEngineSpec.voices vs clonedVoices:** `voices` are statically compiled engine instances. `clonedVoices` are dynamically provisioned from audio files. Only Qwen3 supports cloned voices; the compiler ignores `clonedVoices` for other engine types.

**Alternatives:**
- Factory SPIs produce registries — each factory returns `Map<String, TextToSpeech>`. Same result but via SPI indirection. Rejected in D1.
- Flat voice list without engine grouping — loses engine-specific construction semantics (shared Kokoro engine, Qwen3 voice pool).
**Rationale:** The voice map is the core data model for the migration. Explicit per-engine semantics in the compiler ensure each engine's construction pattern (shared instance, per-voice instance, voice pool) is handled correctly. YAML provides the manifest; the compiler knows the construction recipe.
**Trade-offs:** Per-engine logic in the compiler. This is intentional — engine construction IS engine-specific.
**Sources:** `SpeechProducers.java` (voice registration patterns), `TtsEngineSpec.java`, `TtsVoiceSpec.java`, `Qwen3VoicePool` (cloned voice discovery)
**Exploration:** quick (surfaced by reviewer R1-08)
**Status:** captured

## D9: Type relocations — TtsModelRegistry and CorrectionHooks move to speech-api

**Choice:** Move `TtsModelRegistry` and `CorrectionHooks` from `speech-ws` to `speech-api`.

**TtsModelRegistry** (`record TtsModelRegistry(Map<String, TextToSpeech> models)`) depends only on `TextToSpeech` from `connectors-voice-spi`, which `speech-api` already depends on. It is a speech pipeline value type, not a WebSocket concern. The move is required by D3: the deployment recorder in agentic-yaml must produce `TtsModelRegistry` as a synthetic CDI bean, but agentic-yaml has no dependency on speech-ws — it depends on speech-api (provided scope).

**CorrectionHooks** (`record CorrectionHooks(UnaryOperator<String>, Consumer<String>, Supplier<String>)`) depends only on JDK functional types — zero external dependencies. The move is required by D4: `CorrectionHooks` must be visible from the deployment recorder in agentic-yaml, which cannot see speech-ws types.

**Import resolution after move:** `speech-ws` depends on `speech-api`, so existing references in `SpeechWebSocket.java` and `SpeechSession.java` resolve with a package change. `speech-demo`'s `SpeechProducers.java` transitively sees speech-api through speech-ws.

**Alternatives:**
- Keep in speech-ws, add speech-ws dependency to agentic-yaml — creates a dependency from compilation infrastructure (agentic-yaml) to transport (speech-ws). Architecturally wrong: agentic-yaml should not know about WebSocket transport.
- Extract to a new speech-types module — adds a module for two small records. Unnecessary when speech-api is the natural home for speech pipeline value types.
- Leave in speech-ws, produce beans in speech-ws — reverts D3's transport-independence principle. Rejected in R1-04.
**Rationale:** Both types are speech pipeline value types with no WebSocket semantics. `speech-api` is the existing home for speech pipeline abstractions (`AvatarCognition`, `SpeechPromptAssembler`, `ConversationTurn`, `PerformanceVocabulary`). The design spec (`2026-11-19-avatar-mosaic-component-design.md` §Compiler, line 107) also assumes `TtsModelRegistry` is accessible to the compiler in agentic-yaml.
**Trade-offs:** Breaking change to import paths in speech-ws and speech-demo. Mechanical migration.
**Sources:** `TtsModelRegistry.java`, `CorrectionHooks.java`, `speech-api/pom.xml` (depends on connectors-voice-spi), `agentic-yaml/pom.xml` (depends on speech-api, not speech-ws)
**Exploration:** quick (surfaced by reviewer R2-01, R2-02)
**Status:** captured

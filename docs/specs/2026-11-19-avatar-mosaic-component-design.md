# Avatar Reusable Mosaic Component with YAML Configuration

**Issue:** casehubio/agency#449
**Date:** 2026-11-19

## Problem

The avatar pipeline (3D character + voice + LLM conversation) currently works only as a standalone demo app (`speech-demo`). The CDI wiring is hardcoded in `SpeechProducers`, the frontend is a monolithic `casehub-avatar-panel` in mosaic with fixed layout and hardcoded voice/LLM options, and there is no declarative way to configure the pipeline. Consumers who want an embedded avatar must copy and adapt the demo's Java producers and HTML.

## Design

Two changes across two repos:

1. **Agency** — `AvatarDefinition` YAML spec that declaratively configures the avatar's physical layer (3D model, TTS voices, STT engine, cleanup pipeline). Compiled at build time into CDI beans. REST endpoint serves config to the frontend.
2. **Mosaic** — decompose the monolithic `casehub-avatar-panel` into composable sub-components sharing state through an `AvatarSession` service. Data-driven from the backend config.

### Separation of concerns

The avatar is a "body" for an eidos agent. The YAML configures only the physical rendering and speech pipeline. The agent (referenced by `agentId`) brings the "mind" — LLM routing, personality, cognition, system prompt. These are eidos and platform concerns, not avatar concerns.

| Concern | Owner | Configured via |
|---------|-------|---------------|
| 3D model, camera | Avatar YAML | `model`, `camera-view` |
| TTS voices | Avatar YAML | `tts.engines` |
| STT engine | Avatar YAML | `stt.type` |
| Text cleanup | Avatar YAML | `cleanup` |
| Playback speed | Avatar YAML | `speed`, `min-playback-rate` |
| LLM routing | Eidos agent | `AgentDescriptor.providerConfigs`, `modelFamily` |
| Personality / voice style | Eidos agent | `AgentDescriptor.voice` (`AgentVoiceProfile`) |
| System prompt | Eidos agent | `AgentDescriptor.briefing` |
| Cognition (drives, mood) | Eidos agent | `CognitionDefinition` YAML |
| Tenant identity | Platform runtime | Request-scoped resolution |

## Part 1: YAML Spec (Agency)

### AvatarDefinition

New record in `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/`:

```yaml
avatar:
  agent: mentor-coach
  model: avatar-brunette.glb
  tts-voice: qwen3:jodi_krangle
  camera-view: head
  sample-rate: 16000
  speed: 0.9
  min-playback-rate: 0.6
  tts:
    engines:
      - type: vits
        voices:
          - name: lessac-medium
          - name: amy
            model: vits-piper-en_US-amy-medium
      - type: kokoro
        voices: [af_heart, af_bella, am_adam, bf_emma]
      - type: qwen3
        voices: [ryan, serena, vivian]
        cloned-voices: [jodi_krangle, cori_samuel]
  stt:
    type: whisper
  cleanup:
    max-destructiveness: 2
    filters: [filler-removal, casing, punctuation, grammar]
```

### Java records

```
AvatarDefinition
  agent: String                    # eidos agentId
  model: String                    # 3D model URL/path
  ttsVoice: @Nullable String       # default TTS voice ID
  cameraView: @Nullable String     # head | upper | full (default: head)
  sampleRate: int                  # default: 16000
  speed: double                    # default: 0.9
  minPlaybackRate: double          # default: 0.6
  tts: @Nullable TtsSpec
  stt: @Nullable SttSpec
  cleanup: @Nullable CleanupConfigSpec

TtsSpec
  engines: List<TtsEngineSpec>

TtsEngineSpec
  type: String                     # vits | kokoro | qwen3 | sherpa | audio8
  voices: List<TtsVoiceSpec>       # voice entries
  clonedVoices: @Nullable List<String>  # qwen3 voice cloning

TtsVoiceSpec
  name: String                     # voice identifier (used as registry key, e.g. "lessac-medium")
  model: @Nullable String          # engine-specific model name passed to withDefaults(modelName)
                                   # when null, uses withDefaults() (engine's default model)

SttSpec
  type: String                     # whisper | zipformer
```

### Compiler

`AvatarCompiler` in `agentic-yaml/compiler/`:

- Input: `AvatarDefinition`
- Output: `CompiledAvatar` record containing:
  - Populated `AvatarConfig` values (agentId, model, cameraView, sampleRate, speed, minPlaybackRate)
  - `TtsModelRegistry` — built by instantiating engine classes per `TtsEngineSpec` (reuses existing `VitsTextToSpeech.withDefaults()`, `KokoroTextToSpeech.withDefaults()`, `Qwen3TextToSpeech.withDefaults()` factories)
  - `CleanupConfig` — built from the filter list
  - `SttSpec` — passed through for runtime STT selection
  - JSON representation for the REST endpoint

### Registry pattern

Each TTS engine type maps to a factory:

```
TtsEngineRegistry
  resolve(TtsEngineSpec) -> Map<String, TextToSpeech>
```

Built-in registries for vits, kokoro, qwen3, sherpa, audio8. The compiler iterates `tts.engines`, calls the registry for each, and merges the results into a single `TtsModelRegistry`.

### Deployment

New build step in `AgenticYamlProcessor`:

- Discovers `META-INF/avatar/*.yaml` (or `META-INF/avatar.yaml`)
- Deserializes to `AvatarDefinition`
- Validates (agent field required, at least one TTS engine, voice references resolve)
- Produces `AvatarConfigBuildItem`
- `AgenticRecorder` registers compiled beans at runtime init

New `AvatarConfigBuildItem` in `agentic-yaml-deployment/`.

### REST endpoint

`AvatarConfigResource` — a JAX-RS `@Path("/api/avatar")` endpoint:

- `GET /api/avatar/config` — returns JSON with frontend-relevant config:

```json
{
  "avatarUrl": "avatar-brunette.glb",
  "cameraView": "head",
  "defaultVoice": "qwen3:jodi_krangle",
  "defaultSpeed": 0.9,
  "minPlaybackRate": 0.6,
  "voices": [
    { "id": "lessac-medium", "engine": "vits", "label": "Lessac medium" },
    { "id": "kokoro:af_heart", "engine": "kokoro", "label": "Heart" },
    { "id": "qwen3:ryan", "engine": "qwen3", "label": "Ryan" }
  ],
  "llms": [
    { "id": "claude-haiku-4-5@20251001", "label": "Haiku 4.5" }
  ]
}
```

The `llms` list is populated by querying the eidos `AgentRegistry` for the agent's available models. The `voices` list is derived from the compiled `TtsModelRegistry`.

## Part 2: Mosaic Component Decomposition

### AvatarSession (new)

Extracted from `AvatarWsController`. A plain class (not a Lit controller) extending `EventTarget`:

- Fetches config from `/api/avatar/config` on creation
- Owns the WebSocket connection lifecycle
- Holds mutable state: `turns`, `connectionState`, `currentVoice`, `currentLlm`, `speed`
- Dispatches typed events: `avatar:turn`, `avatar:audio`, `avatar:status`, `avatar:timing`, `avatar:config-loaded`
- Components subscribe via `addEventListener` and unsubscribe in `disconnectedCallback`

```ts
class AvatarSession extends EventTarget {
  config: AvatarConfig | null;
  turns: ConversationTurn[];
  connectionState: 'connecting' | 'connected' | 'disconnected';
  
  constructor(opts: { wsUrl?: string; configUrl?: string });
  connect(): void;
  disconnect(): void;
  sendText(text: string): void;
  sendStart(opts: { sampleRate: number }): void;
  sendStop(): void;
  sendAudio(buffer: ArrayBuffer): void;
  setVoice(voiceId: string): void;
  setLlm(llmId: string): void;
  setSpeed(speed: number): void;
}
```

### Component wiring

The host app creates one `AvatarSession` and passes it to sub-components via property:

```html
<casehub-avatar .session=${session}></casehub-avatar>
<casehub-avatar-controls .session=${session}></casehub-avatar-controls>
<casehub-transcript .session=${session}></casehub-transcript>
<casehub-speech .session=${session}></casehub-speech>
```

### Component responsibilities

| Component | Input from session | Output to session | Renders |
|---|---|---|---|
| `casehub-avatar` | audio queue, mood, config (avatarUrl, cameraView) | playback-complete | 3D scene + viseme lip-sync |
| `casehub-transcript` | turns | — | Chat message bubbles |
| `casehub-speech` | connectionState (disable when disconnected) | start/stop/audio frames | Mic button |
| `casehub-avatar-controls` | config (available voices, LLMs), current selections | voice/LLM/speed changes | Selectors, speed slider |

### casehub-avatar-controls (new)

Data-driven from `AvatarSession.config`:

- LLM selector — populated from `config.llms`
- Voice selector — populated from `config.voices`, grouped by engine
- Speed slider — range from `config.minPlaybackRate` to 1.4
- No hardcoded voice lists or LLM options

### casehub-avatar-panel (refactored)

Becomes a thin convenience shell:

- Creates an `AvatarSession` internally if none provided via property
- Composes `casehub-avatar`, `casehub-avatar-controls`, `casehub-transcript`, `casehub-speech` in a vertical layout
- Passes its session to all children
- Backward compatible — existing consumers who just drop `<casehub-avatar-panel ws-url="/ws/avatar">` still get the full experience

### Changes to existing components

**casehub-avatar:**
- Add `.session` property
- When session is set: subscribe to `avatar:audio` events (replace `audioQueue` property binding), read `avatarUrl` and `cameraView` from `session.config`
- Keep existing property-based API for standalone use (no session)

**casehub-transcript:**
- Add `.session` property
- When session is set: subscribe to `avatar:turn` events (replace `turns` property binding)
- Keep existing `.turns` property for standalone use

**casehub-speech:**
- Add `.session` property
- When session is set: call `session.sendStart/sendStop/sendAudio` directly (replace CustomEvent dispatch)
- Derive disabled state from `session.connectionState`
- Keep existing event-based API for standalone use

## Testing

### Agency

- `AvatarDefinitionSerializationTest` — YAML round-trip for all fields, optional fields, defaults
- `AvatarCompilerTest` — compiles spec to `CompiledAvatar`, verifies TTS registry contains correct voices, cleanup config matches
- `TtsEngineRegistryTest` — each engine type resolves correctly
- Deployment validation — rejects missing agent, invalid engine types

### Mosaic

- `AvatarSession` unit tests — config fetch, WebSocket message routing, event dispatch, state management
- Each component tested independently with a mock `AvatarSession`
- `casehub-avatar-panel` integration test — creates session, verifies sub-components receive events
- `casehub-avatar-controls` — renders correct options from config, dispatches selection changes

## References

- `agency/speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechWebSocket.java` — current WebSocket endpoint
- `agency/speech-ws/src/main/java/io/casehub/agency/speech/ws/AvatarConfig.java` — current MicroProfile config
- `agency/speech-demo/src/main/java/io/casehub/agency/speech/demo/SpeechProducers.java` — current hardcoded CDI wiring
- `agency/agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/` — existing YAML spec patterns
- `agency/agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/` — existing compiler patterns
- `casehub-mosaic/packages/avatar/` — existing mosaic avatar components
- `eidos/api/src/main/java/io/casehub/eidos/api/AgentDescriptor.java` — agent identity
- `eidos/api/src/main/java/io/casehub/eidos/api/AgentVoiceProfile.java` — linguistic personality
- `platform/agent-api/src/main/java/io/casehub/platform/agent/AgentProvider.java` — LLM invocation SPI
- `platform/agent-api/src/main/java/io/casehub/platform/agent/AgentSessionConfig.java` — LLM routing config

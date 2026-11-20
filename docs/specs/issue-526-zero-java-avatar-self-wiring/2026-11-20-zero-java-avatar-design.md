# Zero-Java Avatar Self-Wiring Extension

**Issue:** casehubio/agency#526
**Date:** 2026-11-20
**Status:** Draft

## Goal

Any Pages + Mosaic app gets a talking avatar by adding dependencies and dropping config files. Zero Java code in the consumer app.

## Consumer Experience

A zero-Java avatar app consists of:

```
my-avatar-app/
  pom.xml                                    # dependencies only
  agent-config.yaml                          # LLM credentials
  src/main/resources/
    application.properties                   # wire agent ID
    META-INF/
      avatar.yaml                            # TTS/STT/cleanup config
      eidos/descriptors.yaml                 # agent identity + model tier
      resources/
        index.html                           # imports avatar-component.mjs
```

### pom.xml dependencies

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-agency-speech-deployment</artifactId>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-agency-speech-ws</artifactId>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-connectors-voice-sherpa</artifactId>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-eidos-runtime</artifactId>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-platform-agent-claude</artifactId>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-platform-agent-config</artifactId>
</dependency>
```

### avatar.yaml (TTS/STT pipeline)

```yaml
agent: avatar-assistant
sample-rate: 16000
speed: 0.9
min-playback-rate: 0.6

tts:
  engines:
    - type: kokoro
      voices: [af_heart, am_adam]

stt:
  type: whisper-fallback

cleanup:
  max-destructiveness: 3
```

### descriptors.yaml (eidos agent identity)

```yaml
descriptors:
  - agentId: avatar-assistant
    name: Avatar Assistant
    slot: avatar
    provider: anthropic
    modelFamily: claude
    capabilities:
      - name: conversation
        model:
          tier: FAST
          capabilities: [text]
    briefing: >-
      You are a friendly avatar assistant. Keep responses
      conversational and concise.
```

### agent-config.yaml (credentials)

```yaml
providers:
  - vendor: anthropic
    credential: ${ANTHROPIC_API_KEY}
defaults:
  backend: claude
```

### application.properties

```properties
casehub.avatar.agent-id=avatar-assistant
```

### index.html

```html
<!DOCTYPE html>
<html>
<head>
  <script type="importmap">
  { "imports": {
      "three": "https://cdn.jsdelivr.net/npm/three@0.180.0/build/three.module.js/+esm",
      "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.180.0/examples/jsm/",
      "talkinghead": "./vendor/talkinghead.mjs"
  } }
  </script>
</head>
<body>
  <div id="avatar"></div>
  <div id="controls"></div>
  <div id="chat"></div>
  <script type="module">
    import { AvatarEngine, AvatarControls } from './avatar-component.mjs';
    const engine = new AvatarEngine(document.getElementById('avatar'));
    const controls = new AvatarControls(document.getElementById('controls'), engine);
    await engine.connect();
  </script>
</body>
</html>
```

---

## Work Items

### W1: New `speech-deployment` module

**What:** A standalone Quarkus deployment module that discovers `META-INF/avatar.yaml` and registers TTS/STT/cleanup CDI beans at build time.

**Dependencies:**
- `casehub-agency-agentic-yaml` (with `<exclusion>` for `casehub-agency-core`)
- `casehub-connectors-voice-sherpa`
- `casehub-agency-speech-api`
- `casehub-platform-yaml-jackson`
- `quarkus-arc-deployment`

**Contains (moved from `agentic-yaml-deployment`):**
- `AvatarSpeechProcessor` — build step that registers `TtsModelRegistry`, `TextToSpeech`, `StreamingSpeechToText`, `CleanupConfig`, `CorrectionHooks` as synthetic beans
- `AvatarConfigBuildItem` — build item carrying the parsed `AvatarDefinition`
- Avatar YAML discovery build step — reads `META-INF/avatar.yaml`
- `AvatarConfigBean` + `AvatarConfigResource` registration — `/api/avatar/config` endpoint

**Runtime module:** Uses `AvatarRecorder` from `agentic-yaml` (the existing runtime module for the recorder). The speech-deployment module's `@Record` build steps reference `AvatarRecorder` via the standard Quarkus deployment/runtime split.

**What stays in `agentic-yaml-deployment`:**
- `AgenticYamlProcessor` — still discovers avatar.yaml for full-stack consumers (no duplication — if speech-deployment is on the classpath, its build step takes precedence via `BeanDiscoveryFinishedBuildItem` guards)
- All other build steps (patterns, topologies, cognition, prompt optimisation)

**Result:** Consumer adds `casehub-agency-speech-deployment` → gets TTS/STT/cleanup beans from avatar.yaml. No agency-core on classpath.

### W2: Infrastructure class relocation to `speech-ws`

**Move from `speech-demo` to `speech-ws`:**
- `VoicesEndpoint` → `/api/avatar/voices` (depends on `TtsModelRegistry` from speech-api)
- `JsonPerformanceVocabulary` → `PerformanceVocabulary` SPI impl (depends on speech-api types)
- `DiagnosticEndpoint` → `/diag` health check (depends on `AgentProvider` from platform-agent-api)

These become standard infrastructure that every avatar consumer gets automatically via `speech-ws`'s Jandex index.

**`ModelProvisioningService` + `ModelStatusEndpoint`:** Stay in a sherpa-aware module alongside the `AvatarCompiler` infrastructure. Refactored to derive models from `TtsModelRegistry` entries instead of hardcoded model lists. The provisioning service iterates `TtsModelRegistry.models()` and calls each implementation's `ensureProvisioned()` in a background virtual thread.

### W3: LLM integration via eidos + agent-config

**Delete from `speech-demo`:**
- `SpeechProducers.java` (97-line hardcoded Vertex AI producer)
- `google-auth-library-oauth2-http` dependency

**Add to `speech-demo` dependencies:**
- `casehub-eidos-runtime` (provides `EidosAgentResolver` → `AgentResolver`)
- `casehub-platform-agent-claude` (provides `ClaudeAgentProvider` → `AgentProvider`)
- `casehub-platform-agent-config` (provides `AgentConfigLoader` → credential resolution)

**Wiring:** `SpeechWebSocket` already has `AvatarConfig.agentId()` and `AvatarConfig.tenantId()` declared. The `EidosAgentResolver` resolves the agent descriptor → system prompt + model. `ClaudeAgentProvider` handles invocation via the credentials loaded by `AgentConfigLoader`. Consumer declares their agent in `descriptors.yaml`, credentials in `agent-config.yaml`, and sets `casehub.avatar.agent-id` in `application.properties`.

**What changes in `SpeechWebSocket.onOpen()`:** Currently builds `DefaultPromptAssembler` from `avatarConfig.systemPrompt()`. After this change, if `avatarConfig.agentId()` is present, resolve via `AgentResolver` to get the system prompt and model from the eidos descriptor. Fall back to `avatarConfig.systemPrompt()` for backward compatibility.

### W4: ES module avatar component

**New file:** `speech-ws/src/main/resources/META-INF/resources/avatar-component.mjs`

Extracts three concerns from the 750-line inline `index.html`:

**AvatarEngine class (~400 lines):**
- WebSocket lifecycle (connect, reconnect, message dispatch)
- Audio pipeline (AudioContext, decoding, queued playback, speakAudio)
- Turn state machine (segment tracking, safety timer, playback completion)
- Viseme dispatch and cue handling (gesture triggers, mood management)
- Body animation integration (BodyAnimationSystem, floor state, morph layers)
- TalkingHead lifecycle (init, model switching, mixer registration)

**AvatarControls class (~150 lines):**
- Renders voice selector (fetches from `/api/avatar/voices`), LLM picker, speed slider, animation picker
- Applies defaults from `/api/avatar/config` (model, speed, minPlaybackRate, ttsVoice)
- Polls `/api/models/status` for download progress
- Emits events for user selections

**Exported API:**
```javascript
import { AvatarEngine, AvatarControls } from './avatar-component.mjs';

const engine = new AvatarEngine(document.getElementById('container'), {
  avatarModel: 'avatar.glb',
  cameraView: 'upper',
});
const controls = new AvatarControls(document.getElementById('controls'), engine);
await engine.connect();
```

**Static resources move to `speech-ws`:**
- `avatar-component.mjs` (new)
- `vendor/talkinghead.mjs` (existing)
- `animations/` directory (existing body animation clips + config)

**speech-demo `index.html` becomes ~50 lines:** Imports the module, creates containers, wires chat/mic (application-specific).

### W5: speech-demo cleanup

After W1-W4, speech-demo becomes a minimal consumer app:

**Files retained:**
- `avatar.yaml` — TTS/STT pipeline config
- `META-INF/eidos/descriptors.yaml` — avatar agent identity
- `agent-config.yaml` — credentials
- `application.properties` — `casehub.avatar.agent-id` + Quarkus config
- `index.html` — thin HTML importing `avatar-component.mjs`, chat/mic UI

**Java files: zero.** All infrastructure moved to production modules (speech-ws, speech-deployment).

**pom.xml:** Dependencies only — no Java compilation needed except for Quarkus augmentation.

---

## Module Dependency Graph (after)

```
Consumer app
  ├── speech-deployment (new, build-time)
  │     └── agentic-yaml (with agency-core excluded)
  │           ├── AvatarCompiler, AvatarRecorder, AvatarDefinition
  │           └── speech-api, connectors-voice-spi
  ├── speech-ws (runtime)
  │     ├── speech-api
  │     ├── platform-agent-api (provided)
  │     ├── eidos-api
  │     ├── VoicesEndpoint, DiagnosticEndpoint, JsonPerformanceVocabulary
  │     └── SpeechWebSocket, SpeechSession, avatar-component.mjs
  ├── connectors-voice-sherpa (native TTS/STT impls)
  ├── eidos-runtime (EidosAgentResolver)
  ├── platform-agent-claude (ClaudeAgentProvider)
  └── platform-agent-config (AgentConfigLoader)
```

No `agency-core` anywhere in the dependency tree.

---

## Testing

- **W1:** Unit test: mock `AvatarDefinition` → verify synthetic beans registered. Integration test: Quarkus test with `avatar.yaml` on classpath, inject `TtsModelRegistry`, verify voices match YAML.
- **W2:** Existing `VoicesEndpointTest` moves with the class. Add tests for `JsonPerformanceVocabulary` and `DiagnosticEndpoint`.
- **W3:** Integration test: `descriptors.yaml` + `agent-config.yaml` on classpath, verify `AgentProvider` is resolvable and `AvatarConfig.agentId()` resolves through `EidosAgentResolver`.
- **W4:** Manual browser test: verify avatar loads, voice selector populates, TTS/STT round-trip works.
- **W5:** Verify speech-demo compiles and runs with zero Java files.

---

## Risks

1. **Maven exclusion fragility** (D2): If a future change to `agentic-yaml` adds a new transitive path to agency-core, the exclusion breaks silently. Mitigation: CI enforcer rule that fails if agency-core appears in speech-deployment's resolved dependency tree.
2. **Eidos runtime weight:** Adding `eidos-runtime` brings the agent registry, descriptor loading, and prompt rendering infrastructure. This may be heavier than a minimal avatar consumer needs. Mitigation: eidos-runtime beans are `@DefaultBean` — they only activate if no override exists.
3. **Three YAML files:** The consumer must understand `avatar.yaml` (TTS/STT), `descriptors.yaml` (agent identity), and `agent-config.yaml` (credentials). Mitigation: speech-demo serves as the reference implementation with all three files documented.

---

## References

- `agentic-yaml-deployment/pom.xml` — current deployment module deps
- `agentic-yaml-deployment/AvatarSpeechProcessor.java` — build step to extract
- `agentic-yaml/AvatarRecorder.java` — runtime recorder (stays in agentic-yaml)
- `agentic-yaml/AvatarCompiler.java` — compiler (stays in agentic-yaml)
- `speech-ws/AvatarConfig.java:19-21` — existing agentId/tenantId wiring points
- `speech-ws/SpeechWebSocket.java:37` — AgentProvider injection
- `eidos/runtime/resolver/EidosAgentResolver.java` — AgentResolver implementation
- `platform/agent-claude/ClaudeBeans.java` — ClaudeAgentProvider CDI production
- `platform/agent-config-core/AgentConfigLoader.java` — layered credential resolution
- `platform/agent-config-core/AgentManifestLoader.java:89-104` — load order: seed → system → user → project
- `eidos/examples/agent-scenarios/descriptors.yaml` — descriptor YAML format reference
- `speech-demo/SpeechProducers.java` — hardcoded Vertex AI to delete
- `speech-demo/index.html` — 750-line frontend to extract into avatar-component.mjs

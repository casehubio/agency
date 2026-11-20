---
layout: post
title: "The Extension That Erased the Java"
date: 2026-11-20
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [avatar, quarkus, extension, zero-code, eidos, agent-config]
---

# The Extension That Erased the Java

The speech-demo had seven Java files. Five of them existed only because there was no Quarkus extension to wire the speech pipeline automatically. `SpeechProducers.java` was ninety-seven lines of hardcoded Vertex AI HTTP calls — raw `HttpClient`, manual token refresh, hand-built JSON payloads. `AvatarSpeechProducer.java` was another hundred lines doing what a build step should do: read `avatar.yaml`, call `AvatarCompiler`, produce CDI beans. Three more files — `VoicesEndpoint`, `DiagnosticEndpoint`, `JsonPerformanceVocabulary` — were reusable infrastructure that happened to live in a demo module because there was nowhere else for them to go.

The fix wasn't a refactor of those files. It was making them unnecessary.

## The dependency chain problem

The avatar build-time wiring already existed in `agentic-yaml-deployment`. It discovers `META-INF/avatar.yaml`, parses it into an `AvatarDefinition`, and registers TTS/STT/cleanup beans through `AvatarSpeechProcessor`. But `agentic-yaml-deployment` depends on `agentic-yaml`, which depends on `casehub-agency`, which pulls in `agency-core` — the full orchestration framework. When a consumer adds the deployment module, Quarkus CDI augmentation scans every class on the classpath and fails when it hits orchestration beans that reference types not in the consumer's dependency tree.

The speech-demo worked around this by excluding `agency-core` from its `agentic-yaml` dependency and doing the bean registration manually. Every new consumer would need the same workaround and the same producer files.

## A module with two exclusions

`speech-deployment` is a standalone Quarkus deployment module. It depends on `agentic-yaml` with `agency-core` and `platform-schema-generator` excluded. That's the entire trick — the avatar types (`AvatarDefinition`, `AvatarCompiler`, `AvatarRecorder`) don't import anything from agency-core. They only need `speech-api` and `connectors-voice-sherpa`. The exclusion was already proven in speech-demo's own `pom.xml`; this just promotes it to a first-class module boundary.

The `SpeechAvatarProcessor` is functionally identical to the one in `agentic-yaml-deployment` — same `BeanDiscoveryFinishedBuildItem` guard pattern, same `AvatarRecorder` for speech beans, same `AgenticRecorder` for the config bean. The difference is what it doesn't bring: no `PatternCompiler`, no topology wiring, no orchestration types. A consumer that only wants a talking avatar never sees them.

## Three YAMLs instead of two Java files

The hardcoded `SpeechProducers.java` did three things: create an `AgentProvider`, configure Vertex AI credentials, and set a default model. The platform already has all three — just in separate systems that the demo never composed.

An eidos descriptor declares the avatar agent's identity — model tier, capabilities, system prompt. `agent-config.yaml` provides credentials through the platform's layered resolution (seed catalog → system → user → project). `ClaudeBeans` produces the `AgentProvider` automatically. The existing `AvatarConfig.agentId()` wiring point in `SpeechWebSocket` connects them.

The consumer's three YAML files:

```yaml
# avatar.yaml — what the body does
agent: avatar-assistant
tts:
  engines:
    - type: kokoro
      voices: [af_heart, am_adam]
stt:
  type: whisper-fallback
```

```yaml
# descriptors.yaml — what the mind is
descriptors:
  - agentId: avatar-assistant
    name: Avatar Assistant
    capabilities:
      - name: conversation
        model: { tier: FAST }
    briefing: >-
      You are a friendly avatar assistant.
```

```yaml
# agent-config.yaml — how to reach the LLM
providers:
  - vendor: anthropic
    credential: ${ANTHROPIC_API_KEY}
```

## The frontend followed the backend

The same entanglement existed in `index.html`. Seven hundred and thirty-six lines of inline JavaScript handling WebSocket messages, audio decoding, viseme dispatch, body animations, voice selection, model polling, mic recording, and chat rendering. All in one `<script>` tag.

`avatar-component.mjs` extracts two classes: `AvatarEngine` (WebSocket, audio pipeline, turn state machine, body animation integration) and `AvatarControls` (voice selector, config consumption, model status polling). The demo's `index.html` drops to two hundred lines — it imports the component, creates containers, and wires the application-specific chat and mic UI.

The infrastructure classes moved to `speech-ws` where they belong. `VoicesEndpoint`, `DiagnosticEndpoint`, and `JsonPerformanceVocabulary` are now available to every avatar consumer automatically through the Jandex index. The speech-demo went from seven Java files to two — `ModelProvisioningService` and `ModelStatusEndpoint`, which are genuinely demo-specific.

The goal was never to write less Java. It was to stop writing Java that the platform could already provide.

---
layout: post
title: "The Module That Discovered Twice"
date: 2026-11-26
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [avatar, quarkus, deployment, deduplication, provisioning]
---

# The Module That Discovered Twice

Five small issues left over from the avatar component extraction — the kind of cleanup that accumulates when a larger feature takes all the attention. Each one was a hardcoded value that should have been config-driven, a dropdown that lost its options, or a duplication that nobody had time to resolve.

The interesting one was the duplication between `speech-deployment` and `agentic-yaml-deployment`.

## Two build steps, one YAML file

Both deployment modules had their own `discoverAvatarConfig()` build step scanning for `META-INF/avatar.yaml`. Both parsed it into an `AvatarDefinition`. Both registered an `AvatarConfigBean` and an `AvatarConfigResource`. When only one was on the classpath, fine. When both were — which is what a full-stack consumer does — the same YAML got parsed twice and two processors tried to register the same synthetic beans.

The `hasAppBean()` guards prevented some of the duplication. But `hasAppBean()` deliberately excludes synthetic beans from its check. Two synthetic beans of the same type means ambiguous dependency resolution, and the guard can't see either of them because it's looking for app-provided beans only.

The fix was structural, not clever: `speech-deployment` now depends on `agentic-yaml-deployment` and consumes the `AvatarConfigBuildItem` it produces. Discovery runs once. The speech-specific bean registration — TTS, STT, cleanup, correction hooks — stays in `SpeechAvatarProcessor`, which is the right module for it. The avatar config and REST resource registration stay in `AgenticYamlProcessor`. Two files deleted, 163 lines removed.

The `agentic-yaml-deployment` dependency brings its own exclusion requirements. It transitively pulls `casehub-platform-schema-generator`, which carries `jsonschema-generator:5.0.0` — conflicting with the Anthropic SDK's `4.38.0`. The old `speech-deployment` pom already excluded `agency-core` and `platform-schema-generator` from its `agentic-yaml` dependency. Same exclusions, one level up.

## The provisioning service that provisioned nothing

`ModelProvisioningService` had a hardcoded map: `"kokoro" -> "kokoro-multi-lang-v1_0"`. It ran provisioning in a background thread at startup, tracking status through PENDING, DOWNLOADING, READY, and ERROR states. The UI polled `/api/models/status` to show download progress and disable voice selectors until models were ready.

The thing is, `AvatarCompiler` already provisions everything at bean creation time. `KokoroTextToSpeech.withDefaults()` calls `Provisioner.ensureProvisioned()`. By the time the startup event fires and `ModelProvisioningService.onStartup()` runs, every model is already on disk. The service was provisioning things that were already provisioned.

We replaced the hardcoded map with an injected `TtsModelRegistry`. The engine keys come from the registry's model map — `"kokoro:af_heart"` becomes `"kokoro"` by taking the prefix before the colon. Status is READY at init. The background thread and the provisioning methods are gone.

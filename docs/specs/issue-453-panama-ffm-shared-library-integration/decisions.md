# Decisions — #453 Panama/FFM Shared Library Integration

## D1: Native binary distribution

**Choice:** Build and host pre-built shared libraries ourselves via casehubio/qwentts.cpp fork
**Alternatives:**
- Build from source at provision time — requires cmake + C++ toolchain on target, slower first-run
- Extract from Python wheel — depends on third-party wheel availability and ABI stability
**Rationale:** Full control over versions, checksums, and platform coverage. Same proven pattern as sherpa-onnx native modules (voice-sherpa-native-{platform}).
**Trade-offs:** Ongoing CI burden for cross-platform builds
**Sources:** Provisioner.java (sherpa-onnx native provisioning pattern), voice-sherpa-native-osx-arm64/pom.xml (Maven native module pattern)
**Exploration:** quick
**Status:** captured

## D2: Streaming API

**Choice:** Per-chunk callback API (qt_synthesize_streaming) added to our qwentts.cpp fork, contributed back upstream
**Alternatives:**
- Batch-only initially — loses existing streaming capability that the avatar demo depends on
- Pull-based iterator — more complex FFM binding with cross-call state management
**Rationale:** Streaming already works in the current HTTP setup and the avatar demo depends on it. qwentts.cpp implements streaming internally (tts-server streams PCM frame-by-frame) but doesn't expose it in the C API. Since we're forking anyway, exposing the internal streaming path is low-effort.
**Trade-offs:** Fork diverges from upstream until the contribution is merged
**Sources:** Qwen3TextToSpeech.java:149 (synthesiseStreaming), qwentts.cpp README (streaming synthesis feature description)
**Exploration:** quick
**Status:** captured

## D3: Model format

**Choice:** GGUF only — clean switch from HuggingFace safetensors
**Alternatives:**
- Both formats (GGUF + safetensors) — more provisioning code for backward compat that nothing needs
**Rationale:** The safetensors path only existed for the gabriele-mastrapasqua CLI binary which we're replacing. Pre-converted GGUFs available at Serveurperso/Qwen3-TTS-GGUF on HuggingFace. Smaller files via quantization (Q8_0/Q4_K_M).
**Trade-offs:** No fallback to safetensors path if GGUF models have issues
**Sources:** Provisioner.java:749-820 (current safetensors provisioning), HuggingFace Serveurperso/Qwen3-TTS-GGUF
**Exploration:** quick
**Status:** captured

## D4: Voice cloning mechanism

**Choice:** In-process extraction via qt_extract_voice_ref() from reference WAV files
**Alternatives:**
- Keep .qvoice files — ties us to a format defined by the old CLI binary
- Support both — migration path but more code to maintain
**Rationale:** qt_extract_voice_ref() computes speaker embedding + RVQ codes directly from reference WAV. Eliminates the .qvoice pre-processing step and separate qwen-codec tool. Follows same VoiceRegistry pattern as Audio8TextToSpeech.
**Trade-offs:** Existing .qvoice files become unusable (need re-extraction from original WAV sources)
**Sources:** Qwen3VoicePool.java (current .qvoice loading), Audio8TextToSpeech.java (VoiceRegistry pattern)
**Exploration:** quick
**Status:** captured

## D5: Fork location

**Choice:** casehubio/qwentts.cpp in the casehubio GitHub org
**Alternatives:**
- Personal fork — less discoverable for the team
- Vendor into connectors — harder to stay in sync with upstream
**Rationale:** CI builds shared libraries for all platforms on tag. Release assets are the pre-built binaries that Provisioner downloads. Consistent org-level hosting.
**Trade-offs:** Org repo maintenance burden
**Sources:** casehubio GitHub org conventions
**Exploration:** quick
**Status:** captured

## D6: Architecture approach

**Choice:** Single Qwen3Library + rewrite Qwen3TextToSpeech in-place (Approach A)
**Alternatives:**
- New Qwen3NativeTextToSpeech alongside existing — doubles maintenance, keeps process management code
**Rationale:** The HTTP path exists only because there was no shared library. StreamingTextToSpeech interface means consumers don't change. Qwen3VoicePool simplifies from process management to a ConcurrentHashMap of extracted voice refs.
**Trade-offs:** No HTTP fallback if FFM binding has platform-specific issues
**Depends on:** D1 (native binaries available), D2 (streaming API in fork)
**Sources:** SherpaLibrary.java (FFM binding pattern), Qwen3TextToSpeech.java (current HTTP impl), Qwen3VoicePool.java (current process management)
**Exploration:** quick
**Status:** captured

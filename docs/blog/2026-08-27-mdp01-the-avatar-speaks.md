---
entry_type: note
subtype: diary
title: "The avatar speaks"
date: 2026-08-27
author: mdp
tags: [speech, avatar, tts, stt, kokoro, lip-sync, talkinghead]
series: speech-pipeline
refs:
  - casehubio/blocks#207
  - casehubio/blocks#205
  - casehubio/blocks#208
  - casehubio/blocks#209
  - casehubio/blocks#210
---

Four issues went in today, but the session was really about one question: can you talk to a 3D avatar and have it talk back with its lips moving?

The answer is yes — with qualifications.

## Three voice engines, one interface

We started with the simplest issue: wire `SherpaOnnxTextToSpeech` as an alternative to the existing `VitsTextToSpeech`. Same Piper models, different inference pipeline — sherpa-onnx's C API vs direct onnxruntime. Both implement `TextToSpeechService`, both get a `withDefaults()` factory that auto-provisions native libraries and models. The difference is that VitsTextToSpeech extracts per-phoneme timing from the VITS duration predictor (enabling lip-sync), while sherpa-onnx returns empty phonemes. Same voice quality, different data.

Then Kokoro. This is a StyleTTS2-based model — 82 million parameters, 24kHz output, noticeably more natural than Piper VITS. The research initially looked daunting because Kokoro uses a sophisticated G2P pipeline (misaki) that requires spaCy POS tagging and a BART fallback model. Reimplementing that in Java would have been a project in itself. But sherpa-onnx v1.13.6 already supports Kokoro natively, handling G2P internally via espeak-ng and lexicons. So `KokoroTextToSpeech` ended up being a clean parallel to `SherpaOnnxTextToSpeech` — same FFM pattern, different struct offsets in the sherpa-onnx C API config.

The demo now offers 22 voices across three engines: 5 Piper VITS (direct), 4 Piper via sherpa, 11 Kokoro. Each carries a MOS (Mean Opinion Score) rating in the dropdown — Piper around 3.7, Kokoro around 4.3. The quality gap is audible.

## Microphone to transcript

Wiring streaming STT was mechanical — `SherpaOnnxStreamingSpeechToText` already existed, the Zipformer transducer model just needed provisioning. The interesting problem was at the edges.

The streaming decoder works in chunks. When you stop talking and call `finalResult()`, the last partial chunk hasn't been decoded yet — the trailing words are silently lost. The fix is counterintuitive: feed a second of silence into the decoder before reading the result. This gives it enough trailing context to commit the final hypothesis. Without this, "hello my name is Mark" would consistently come back as "hello my name".

The browser side had its own timing issue. The mic button would send audio frames, but `recording = false` was set the instant you clicked stop — killing the `onaudioprocess` callback before the 500ms delay that was supposed to let remaining audio flow. The audio frames stopped but the stop message was sent 500ms later to an already-empty buffer. We traced this through console logs: 55 frames sent, stop at 500ms, but the decoder only had audio up to the click moment.

## A patching bug surfaces

When the server started for the first time with all voice engines registered, it crashed: `Graph output (/Ceil_output_0) does not exist in the graph.`

`ModelPatcher` adds the VITS duration predictor as a graph output to enable phoneme timing. It patches every Piper model on first provisioning. But some models — like `ryan-high` — don't have a node named `/Ceil_output_0`. The patcher was adding a graph output that referenced a non-existent computation node, and onnxruntime rejected the entire model on load.

The fix was validation: check `hasNodeOutput()` before patching. Models without the expected node simply skip the patch and report `phonemeTimingAvailable = false`. We also added `.unpatched.onnx` backups so the sherpa-onnx engine can load the original model while VitsTextToSpeech uses the patched version.

## The 3D avatar

TalkingHead loaded cleanly — a Ready Player Me model with all 15 Oculus viseme blend shapes on four meshes. The server generates viseme timelines from phoneme timing, the client passes them to `speakAudio()`. On paper, everything was wired.

The lips didn't move.

I proved the blend shapes worked by directly setting `morphTargetInfluences[viseme_aa] = 1.0` — mouth opened immediately. And `speakAudio()` was entering `speaking=true` state. But the morph target values never changed during playback.

The root cause was the browser event loop. `speakAudio` was called from an async WebSocket message handler — inside a microtask continuation after `await decodeAudioData()`. TalkingHead's internal animation scheduling doesn't properly initialise from the microtask queue. Wrapping the call in `setTimeout(fn, 0)` — breaking out to the macrotask queue — fixed it. The morph targets now spike to 0.72 for vowels and 0.85 for plosives during speech.

The movement is technically there but visually subtle — transitions spike and decay within 200ms. Tuning the amplitude and hold times is filed as #210.

## What's next

The lip-sync amplitude needs tuning before the avatar looks convincingly alive. The Tier 2 quality pass (#196) is next — VAD to stop transcribing silence, streaming TTS for lower latency, and model benchmarking for accuracy data. The social cognition stack (personality, mood, drives, mental models) is built but not yet wired into the speech pipeline — that's Tier 2 issues #198–#203.

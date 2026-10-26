---
title: "The Pipeline That Worked On Paper"
date: 2026-08-28
author: mdp
entry_type: note
subtype: diary
projects:
  - casehubio/blocks
series: issue-212-stt-accuracy-pipeline
tags: [lip-sync, speech-recognition, whisper, symspell, avatar, kokoro, sherpa-onnx, tts]
---

# The Pipeline That Worked On Paper

CaseHub already had everything an avatar needs to be interesting — personality that drifts through conversation, a mood system on the PAD model, drives for curiosity and affiliation, mental models that track what you know. What it didn't have was a voice. Building one meant solving three problems: hear the user, respond audibly, and move the lips while talking. Every component passed its tests. None of them worked the first time a human tried them.

## Three Ways To Say the Same Thing

The first voice engine was VitsTextToSpeech — direct onnxruntime inference on Piper VITS models, with espeak-ng handling phonemization. It extracts per-phoneme timing from the VITS duration predictor, which is what makes lip-sync possible. The second, SherpaOnnxTextToSpeech, wraps the same Piper models through sherpa-onnx's C API. Same voice quality, different failure surface — an A/B comparison at the inference layer, no phoneme timing from this path.

The interesting engine is Kokoro. StyleTTS2 architecture, 82 million parameters, Apache 2.0. I initially assumed integrating it would mean porting the `misaki` G2P pipeline to Java — spaCy POS tagging, a BART fallback model for unknown words, multiple lexicon files. Then I checked the sherpa-onnx C API header for our version (1.13.6) and found `SherpaOnnxOfflineTtsKokoroModelConfig` already defined. Sherpa-onnx handles Kokoro's G2P internally. `KokoroTextToSpeech` ended up as a parallel to `SherpaOnnxTextToSpeech` — same FFM pattern, different struct offsets.

The quality difference is audible. Piper VITS runs about MOS 3.7. Kokoro hits 4.3. On the MOS scale, where 5.0 is indistinguishable from human speech, that gap is the difference between "clearly synthesised" and "wait, is that a recording?" The demo registers 22 voices across all three engines. Switching at runtime is a dropdown.

## The Words That Vanished

Wiring the browser microphone to the streaming Zipformer recognizer was mechanical. The non-obvious problem was at the boundary.

I'd say "hello my name is Mark" and the transcript came back "hello my name." Every time. Consistent truncation of the last one to three words.

The streaming Zipformer processes audio in 160ms chunks with look-ahead context. When you call `finalResult()`, the decoder returns what it's committed. The last partial chunk hasn't accumulated enough frames. Those trailing words are buffered internally but never processed, and there's no "input finished" API in sherpa-onnx's online recognizer.

The fix: feed one second of silence at 16kHz before reading the result. The decoder gets enough trailing context to flush its chunk buffer. Counterintuitive — padding real speech with fake audio — but standard in streaming ASR. The kind of thing you only discover by watching transcripts get truncated.

## The Model That Broke Its Sibling

When the demo started with all three engines registered, onnxruntime rejected the ryan-high model: "Graph output (/Ceil_output_0) does not exist in the graph."

ModelPatcher adds the VITS duration predictor as an ONNX graph output to enable phoneme timing. It patches every Piper model on first provisioning. The problem: not every Piper variant has a node named `/Ceil_output_0`. The patcher was adding a graph output referencing a non-existent node, and onnxruntime treated the entire model as corrupt — breaking it for both VitsTextToSpeech AND SherpaOnnxTextToSpeech, which share the same model directory.

Two fixes. Validation: check `hasNodeOutput()` before patching. Coexistence: save the original as `.unpatched.onnx` so the sherpa-onnx engine loads the unmodified model while VitsTextToSpeech uses the patched version.

## The Mouth That Moved Too Fast

With all that working, I loaded a Ready Player Me GLB model in TalkingHead. Fifteen Oculus viseme blend shapes across four meshes. The VITS phoneme timing was precise to the millisecond. TalkingHead's `speakAudio` accepted our data without complaint. The morph targets moved — viseme_O hit 0.72, viseme_PP reached 0.85.

On screen, the mouth didn't move.

The constraint was `acc` — TalkingHead's acceleration limit on morph target animation: 0.01 per second squared. For a 100ms phoneme, maximum displacement from rest is `0.5 × 0.00001 × 100² = 0.05`. Invisible at any screen size. The values spiked, but the decay was acceleration-limited, and with phonemes lasting 50–150ms, the mouth shape never held long enough for a human eye to register.

The fix was to bypass TalkingHead's animation system entirely. We drive `morphTargetInfluences` directly on the Three.js meshes via `requestAnimationFrame`, with lerp-based interpolation — fast attack (0.35) for visible ramp-up, slow decay (0.12) for natural fade. Each viseme carries a per-type weight: open vowels at 1.0, bilabials at 0.8, consonants at 0.4–0.6, silence at zero.

## The Bug That Only Showed Up Second Time

First request: lip-sync worked. Second request: audio played, mouth stayed still.

The root cause was a shared mutable variable — `pendingVisemes` — consumed across an async gap. The WebSocket handler set it when phonemes arrived; `playAudio` consumed it after `await blob.arrayBuffer()`. For multi-sentence responses, the server sends phonemes-audio pairs in rapid succession. Between `await` yield points, subsequent phonemes overwrote the variable before the first audio finished decoding.

Sentence one got the wrong visemes. Sentence two got none. Both audio queues played simultaneously. Three bugs from one root cause.

The fix: capture `pendingVisemes` synchronously at the top of `playAudio` — before any `await`. One unified `playQueue` where each item carries its own audio buffer and viseme timeline. The two-queue architecture collapsed into one path.

## Then the Ears Needed Work

With the mouth working, the ears became the problem. "Can you read me a limerick?" → "CAN YOU READ ME A RELIMBERATE PLEASE."

The streaming Zipformer is fast and lightweight, but its vocabulary coverage is narrow. Uncommon words — *limerick*, *filler*, domain terms — get mangled into whatever phonetically adjacent common word the model knows. The correction pipeline literature is clear: zero-shot LLM correction makes things worse. Over-correction is the primary failure mode.

## whisper.cpp Over Everything Else

Keep Zipformer and compensate with heavy post-correction, or replace the engine? Whisper is substantially more accurate but doesn't stream.

The trick is pseudo-streaming: buffer audio, re-run Whisper periodically on the growing buffer, diff against the previous result. whisper.cpp has a purpose-built C API — ten functions, clean FFM surface. On CPU for single-user inference, slightly faster and leaner than CTranslate2. And it exposes `initial_prompt` — decoder conditioning that biases recognition toward expected vocabulary. Sherpa-onnx's Whisper wrapper doesn't have this.

Fourth FFM binding, same pattern as SherpaLibrary, OnnxRuntimeLibrary, and EspeakLibrary. Shared `whisper_context` with a `ReentrantLock` for serialized inference, 30-second bounded audio buffer to prevent O(n²) re-inference on long utterances.

## The Correction Pipeline

Even Whisper makes errors on uncommon words. SymSpell handles edit-distance corrections — O(1) lookups via pre-computed delete dictionary. But "relimberate" is edit distance 7 from "limerick." SymSpell caps at 2. This is where Double Metaphone comes in — phonetic codes that see similarity edit-distance can't. The pipeline unions candidates from both sources, ranks by bigram context: `P("a limerick") >> P("a relimberate")`.

One design decision I'm pleased with: the LLM response as a vocabulary oracle. When the avatar responds with "Here's a limerick for you," the word "limerick" feeds back into both the Whisper `initial_prompt` and the SymSpell dictionary. The LLM understood the intent — we're harvesting the correct spelling from its response.

The full chain: Whisper (vocabulary-biased decoder) → TranscriptCorrector (SymSpell + Metaphone + n-gram) → filler removal → GECToR grammar → punctuation → casing. All ONNX or pure Java. No LLM in the critical path.

# M2M Animation Library Integration — Design Spec

**Issue:** casehubio/agency#457
**Date:** 2026-11-18
**Decisions:** See `decisions.md` for full rationale, alternatives, and trade-offs (D1–D17, reviewed across 3 adversarial rounds).

## Overview

Wire the M2M animation library (68 clips authored in #458) into TalkingHead's speech pipeline with proper audio-gesture synchronisation, turn framing, morph target compositing, and mood ownership. The result is a production-grade avatar speech pipeline, not a demo integration.

## Prerequisites (D1, D17)

1. **Land #458** via `work-end` first. Recreate #457 branch from updated main.
2. **Connectors peer issue** (mark timing SPI) must land before #457 implementation starts.
3. **Neocortex peer issue** (proactive prompt sections, non-verbal outcomes) follows #457, non-blocking.

## Architecture

```
LLM → response text with [gesture:tag] and [mood:tag]
  ↓
UtteranceSegmenter (D2): single parser for all speech paths
  → ParsedSegment(cleanText, List<Cue>)
  ↓
Per consumer form (D9):
  TTS         → clean text
  Client      → Segment(text, visemes, cues) + binary audio w/ header
  History     → canonical markup (validated tags only)
  Vocabulary  → clean text (no tag pollution)
  Cognition   → clean text
```

### Protocol (D3)

Replaces `Phonemes` and `Response` with turn-framed messages:

| Message | Direction | Purpose |
|---------|-----------|---------|
| `Segment(turnSeq, index, text, hasAudio, visemes, cues)` | server→client | Per-sentence: display text + visemes + gesture/mood cues |
| `TurnComplete(turnSeq, segmentCount, origin)` | server→client | End-of-turn signal |
| `Gesture(tag, origin)` | server→client | Non-speech gestures (attention bids, cognition) |
| `Mood(state)` | server→client | Persistent mood baseline (cognition-owned) |
| `PlaybackComplete(turnSeq)` | client→server | Client finished playing turn's audio |
| `UserActivity(kind)` | client→server | Typing, visibility changes |

Binary audio frames carry an 8-byte header: `u8 version, u8 reserved, u16 segmentIndex, u32 turnSeq`. Client pairs frames to segments by identity, not arrival order.

### Gesture Timing (D4)

The server owns speech time via SSML-style mark timing:
- `SynthesisOptions` gains `List<TextMark>` marks
- `SynthesisResult` gains `List<MarkTiming>` with millisecond offsets
- **VITS: exact, guarded** — espeak phonemisation + duration output word boundaries, with consistency guard
- **Phoneme-only engines: phoneme-proportional** fallback
- **No phonemes: character-proportional** fallback
- `MarkTimingEnricher` decorator provides fallback tiers

The client owns animation time: `trigger = max(0, offsetMs/rate − strokeMs − crossfade/2)` where `strokeMs` is per-clip metadata from the performance vocabulary.

### Session Concurrency (D10)

SpeechSession splits into two sides:
- **Capture side** (inbound thread): STT stream, ring buffer, speaker ID, partial polling
- **Turn side** (single-thread TurnExecutor): history, prompt assembly, LLM, TTS, outbound messages

They share no mutable state. The capture side hands immutable `UserTurn` requests to the executor. One active turn per session. Proactive turns are suppressed during listening/thinking/unacknowledged playback, and re-checked at dequeue for staleness.

### Client Floor States (D13)

| State | Entered on | Base pool |
|-------|-----------|-----------|
| idle | TurnComplete + all audio played | `idle` |
| listening | mic start, UserActivity(typing) | `listening` |
| thinking | user stop/text sent | `thinking` (neutral attentive stances) |
| speaking | first audio-bearing segment plays | `speaking` |

### Animation Layering (D11)

Two concurrent layers in `BodyAnimationSystem`:
- **Base layer**: looping pool clip. Pool changes only swap the base.
- **Gesture layer**: one-shot clip. Base ducks to weight 0 during gesture, fades back when gesture finishes.

A pool change during a gesture swaps the base underneath with no visible effect.

### Face Compositing (D6, D14)

One `scene.onBeforeRender` hook orders all writers per frame:
1. TalkingHead: blink, mood, eyes (base)
2. Visemes: replace `viseme_*` channels
3. Gesture expression curves: added to expression channels, clamped to [0,1], weight follows gesture layer

Morph target tracks are stripped from body clips at load. Expression curves (brow, mouthFrown, mouthSmile, cheek, noseSneer, mouthPucker) are extracted name-keyed from the animation bundle's mesh dictionary, applied through the avatar's dictionary.

### Performance Vocabulary (D12)

Single source of truth: `avatar-performance.json`
- `clips`: every GLB clip with `strokeMs` and optional expression-curve overrides
- `taxonomy`: categories → subcategories → weighted clips
- `pools`: per floor state → weighted subcategory refs
- `gestures`: tag + description + target + `promptable` flag
- `moods`: TalkingHead mood set with descriptions and `promptable`

`body-animations.mjs` holds no data — pure playback logic. Java validates via `PerformanceVocabulary` SPI in speech-api.

### LLM Prompt (D7)

`GesturePromptSection` (speech-api `PromptSection`) generated from vocabulary:
- Each promptable tag with one-line description
- Rules: place tag before its word, at most one per sentence, none when nothing fits
- `DefaultPromptAssembler` gains `PromptSection` composition (first production composer of that SPI)
- `SpeechPromptAssembler.assemble` gains `PromptContext` parameter

### Mood Ownership (D16)

One owner per session, by configuration:
- **Cognition with mood**: `AvatarCognition.currentAffect()` → PAD vector → discrete mood via octant mapping with hysteresis + dead-zone. `Mood(state)` message on change.
- **No cognition**: LLM owns mood. `[mood:X]` → turn-scoped `MoodCue`, decays after playback + 3s hold.

### Idle Attention (D8)

Server-owned, opt-in via `AvatarConfig`:
- **With cognition**: non-verbal bids via neocortex (D17 follow-up). Until then, no attention gestures.
- **Without cognition**: `SilenceAttentionPolicy` — 30s subtle bid, 75s stronger bid, then nothing.
- Activity resets: user messages, `PlaybackComplete`, throttled `UserActivity`.

### Keyword Fallback — Dropped (D15)

No keyword fallback. The speaking pool provides continuous motion. Tag adherence is logged per turn. Revisit data-driven if models ignore instructions.

## Verification Spike (D14)

Before implementation planning: verify TalkingHead 1.8 `speakAudio`:
- (a) accepts our viseme timeline and markers
- (b) markers fire on the audio clock
- (c) body-pose system can be prevented from writing non-head bones

If all pass, adopt as playback host. If any fails, keep custom audio queue.

## Cross-Repo Changes

| Repo | Change | Blocking? |
|------|--------|-----------|
| connectors | `TextMark`/`MarkTiming` in voice-spi, `MarkTimingEnricher`, VITS guarded-exact | Yes — blocks #457 |
| agency (speech-api) | `PerformanceVocabulary` SPI, `PromptSection` composition, `PromptContext` param, `Affect` record, `AvatarCognition.currentAffect` | In-repo |
| agency (speech-ws) | `UtteranceSegmenter`, `Segment`/`TurnComplete`/`Gesture`/`Mood` protocol, capture/turn split, `TurnExecutor`, `SilenceAttentionPolicy`, `GesturePromptSection`, `MoodMapper` | In-repo |
| agency (speech-demo) | `avatar-performance.json`, `body-animations.mjs` data-free rewrite, `index.html` floor states + layered animation + face compositor + new protocol | In-repo |
| neocortex | Caller-supplied proactive prompt sections, non-verbal proactive outcome, delivery feedback | No — follows #457 |

## Testing

- **Build-time**: vocabulary ↔ GLB clip inventory, taxonomy resolution, expression channel names
- **Unit**: `UtteranceSegmenter` parsing (forward binding, trailing tags, malformed markup, concurrent tags)
- **Unit**: `GestureExtraction` per consumer form (clean, canonical, vocabulary-filtered)
- **Unit**: Turn serialisation (capture/turn split, proactive suppression, dequeue staleness check)
- **Unit**: `MoodMapper` PAD→discrete with hysteresis and dead-zone
- **Unit**: `MarkTimingEnricher` fallback tiers
- **Integration**: `Segment`/audio pairing by header identity
- **Manual**: gesture fires at correct word during speech
- **Manual**: TTS does not vocalize tags
- **Manual**: floor state transitions (idle→listening→thinking→speaking→idle)
- **Manual**: morph target expressions during idle, clean visemes during speech
- **Manual**: attention bids at 30s/75s, reset on activity

## References

- [SpeechSession.java] — server speech pipeline, handleStop L239-245, handleProactiveSpeech L175-176
- [AvatarMessage.java] — current WebSocket protocol (to be replaced)
- [body-animations.mjs (#458)] — BodyAnimationSystem, GESTURE_MAP, TAXONOMY
- [index.html (#458)] — TalkingHead 1.8 integration, playback queue, viseme loop
- [casehub-connectors-voice-spi] — SynthesisOptions, SynthesisResult, LipSyncEnricher
- [CognitionAvatarAdapter] — wrapAssembler L51-75, evaluateProactive L94-103
- [MoodOrchestrator] — currentMood(agentId, tenantId) → Optional<MoodState>
- [Azure avatar SSML gestures](https://learn.microsoft.com/en-nz/azure/ai-services/speech-service/text-to-speech-avatar/avatar-gestures-with-ssml)
- [AIAvatarKit](https://github.com/uezo/aiavatarkit) — LLM → animation pipeline
- [SARGes](https://arxiv.org/pdf/2503.20202) — LLM ethogram-based gesture labels
- [TANDE](https://arxiv.org/pdf/2607.13357) — structured JSON gesture output
- McNeill 1992 / Kendon 2004 — gesture stroke–affiliate synchrony

# Mixamo Avatar Body Animation — Design

> **Issue:** casehubio/agency#450
> **Date:** 2026-11-11
> **Status:** Design
> **Branch:** issue-450-mixamo-avatar-animation

## 1. Problem Statement

The 3D avatar in the speech demo is mostly static — the built-in TalkingHead `playGesture()` offers only 8 minimal bone-rotation poses (handup, thumbup, shrug, etc.), and the demo uses just 3 of them with naive keyword matching. The avatar needs expressive full-body movement: thinking while the LLM generates, explaining gestures while speaking, nodding in agreement, waving hello, fidgeting during idle.

## 2. Approach

Load Mixamo FBX animations via TalkingHead's built-in `playAnimation(url)` API. TalkingHead v1.8 has native Mixamo support — it auto-strips `mixamorig` bone prefixes, handles the 100:1 scale conversion, caches loaded clips, and plays them on the avatar armature via Three.js `AnimationMixer`.

**Trigger architecture: Hybrid.** Client-side keyword heuristics handle the common case (zero latency). A new `AvatarMessage.Animation` WebSocket message type enables server-side cues for future cognition integration (Tier 2), where the cognitive layer or LLM system prompt can inject synchronized animation cues alongside speech.

## 3. Animation Set

~15-20 Mixamo FBX animations, downloaded "Without Skin" format, bundled as static resources in `speech-demo/src/main/resources/META-INF/resources/animations/`.

### 3.1 Curated Categories

| Category | Example Animations | When triggered |
|----------|-------------------|----------------|
| **Thinking** | Chin touch, looking up, head scratch | LLM generating, response contains "let me think", "hmm", "consider" |
| **Explaining** | Hand gesture variations (2-3) | Long responses, "here's how", "the reason is", "because" |
| **Agreement** | Nodding, thumbs up | "yes", "right", "exactly", "agree", "correct" |
| **Greeting** | Waving, friendly wave | "hello", "hi", "hey", "welcome", start of conversation |
| **Surprise** | Surprise reaction | "wow", "really", "amazing", "incredible", "!" emphasis |
| **Confusion** | Head scratch, shrug | "not sure", "hmm", "I don't know", "?" questions |
| **Happy** | Laughing, excited clap | "great", "wonderful", "fantastic", "haha" |
| **Sad** | Disappointed, head down | "sorry", "unfortunately", "sad", "too bad" |
| **Defensive** | Arms crossed | "however", "but", "on the other hand", "disagree" |
| **Pointing** | Pointing gesture | "look at", "here", "this", "that", directional language |
| **Idle** | Weight shift, look around, subtle fidget (2-3 variants) | No speech for 5+ seconds, between turns |

Each category has 1-3 animation variants to avoid repetition. Random selection from the pool.

### 3.2 Animation Metadata

Each animation entry in the JS config includes:

```javascript
const ANIMATIONS = {
  thinking: {
    keywords: ["let me think", "hmm", "consider", "wondering"],
    anims: [
      { url: "animations/thinking-1.fbx", dur: 4, exclusive: false },
      { url: "animations/thinking-2.fbx", dur: 3, exclusive: false }
    ]
  },
  greeting: {
    keywords: ["hello", "hi", "hey", "welcome"],
    anims: [
      { url: "animations/waving.fbx", dur: 3, exclusive: true }
    ]
  },
  idle: {
    keywords: [],  // triggered by timeout, not keywords
    anims: [
      { url: "animations/idle-1.fbx", dur: 8, exclusive: false },
      { url: "animations/idle-2.fbx", dur: 10, exclusive: false }
    ]
  }
  // ... etc
};
```

- `dur` — seconds to play (passed to `playAnimation`)
- `exclusive` — if true, suppress viseme driving during playback (full-body animations like waving). If false, body animation layers with lip-sync.

## 4. Animation Layering

Body animations (bone transforms via `playAnimation`) and lip-sync (morph target blend shapes via the viseme timeline) operate on independent systems:

- **Non-exclusive (default):** `playAnimation` runs on the armature. Viseme morph targets continue updating via `requestAnimationFrame`. The avatar explains with hand gestures while speaking. No code change needed — they're already independent.

- **Exclusive:** When `exclusive: true`, the client sets a flag that suppresses the viseme `requestAnimationFrame` loop for the duration. Visemes reset to `sil` on animation start. Resume viseme driving when the animation ends (via TalkingHead's `finished` event or `stopAnimation()`).

## 5. WebSocket Protocol Extension

### 5.1 New message type

Add to `AvatarMessage.java`:

```java
record Animation(String category, @Nullable String url, 
                 @Nullable Integer durationSec) implements AvatarMessage {}
```

- `category` — animation category name (e.g., "thinking", "greeting"). Client looks up the category in its `ANIMATIONS` map and plays from the pool.
- `url` — optional direct URL override. If provided, plays this specific FBX regardless of category.
- `durationSec` — optional duration override. Falls back to the animation's default.

### 5.2 Server → Client flow

```json
{"type": "animation", "category": "thinking"}
```

Client receives this, looks up the category, picks a random animation from the pool, calls `head.playAnimation(url, null, dur)`.

### 5.3 Priority

Server-side `animation` messages override client-side heuristics. If the client was about to trigger a heuristic animation and a server cue arrives for the same response, the server cue wins. Implementation: the client checks a `serverAnimationReceived` flag before applying heuristic classification.

### 5.4 MessageCodec changes

Add encode/decode for the `Animation` variant in `MessageCodec.java`. Follow the existing pattern for other message types.

## 6. Client-Side Heuristic Classifier

Replace the existing 3-line `playGesture` keyword matching in `index.html` with:

### 6.1 Classification function

```javascript
function classifyResponse(text) {
  const lower = text.toLowerCase();
  for (const [category, config] of Object.entries(ANIMATIONS)) {
    if (config.keywords.length === 0) continue;  // skip idle (timeout-based)
    for (const kw of config.keywords) {
      if (lower.includes(kw)) return category;
    }
  }
  return null;  // no match — no animation
}
```

First match wins. Category ordering in the `ANIMATIONS` object determines priority (greeting > thinking > explaining > ... > idle).

### 6.2 Trigger points

| Trigger | Animation | When |
|---------|-----------|------|
| Response received | Classify response text → play matching animation | `case 'response':` in `ws.onmessage` |
| Processing started | Play "thinking" animation | After `sendText()` or `stopRecording()`, before response arrives |
| Idle timeout | Play random idle animation | 5+ seconds with no speech or interaction |

### 6.3 Idle animation loop

```javascript
let idleTimer = null;
function resetIdleTimer() {
  clearTimeout(idleTimer);
  idleTimer = setTimeout(() => {
    if (head && !isPlaying) {
      const idle = ANIMATIONS.idle;
      const anim = idle.anims[Math.floor(Math.random() * idle.anims.length)];
      head.playAnimation(anim.url, null, anim.dur);
      resetIdleTimer();  // loop
    }
  }, 5000 + Math.random() * 3000);  // 5-8 second jitter
}
```

Reset on any user interaction (message sent, mic used) or avatar speech.

## 7. UI — Animation Picker

Add a control section for manually triggering animations. Placed in the existing `#controls` div.

```html
<label>Animation: <select id="animPicker">
  <option value="">Auto</option>
  <optgroup label="Conversational">
    <option value="thinking">Thinking</option>
    <option value="explaining">Explaining</option>
    <option value="agreement">Agreement</option>
    <option value="greeting">Greeting</option>
  </optgroup>
  <optgroup label="Expressive">
    <option value="surprise">Surprise</option>
    <option value="happy">Happy</option>
    <option value="sad">Sad</option>
    <option value="defensive">Defensive</option>
    <option value="pointing">Pointing</option>
    <option value="confusion">Confusion</option>
  </optgroup>
  <optgroup label="Idle">
    <option value="idle">Idle Fidget</option>
  </optgroup>
</select></label>
<button id="playAnim" title="Play selected animation">▶</button>
```

- "Auto" (default) — heuristic classification active
- Selecting a specific animation + clicking play → immediately triggers it
- The picker is for demo/debugging; auto mode is the expected production flow

## 8. Mixamo FBX Sourcing

### 8.1 Download process

1. Go to mixamo.com (free, requires Adobe account)
2. Select each animation by name
3. Download as FBX, "Without Skin" format (animation data only, no mesh)
4. Files are typically 50-300KB each

### 8.2 Naming convention

`<category>-<variant>.fbx` — e.g., `thinking-1.fbx`, `idle-shift.fbx`, `greeting-wave.fbx`.

### 8.3 Licensing

Mixamo animations are royalty-free for use in projects. The downloaded FBX files contain only animation data (bone keyframes), no proprietary mesh or texture. They can be redistributed as part of an application.

### 8.4 "In Place" setting

For all animations, use the "In Place" checkbox in Mixamo's download settings. This prevents root motion drift — the avatar stays at its position rather than walking off-screen.

## 9. File Changes

### 9.1 speech-ws (Java — server side)

| File | Change |
|------|--------|
| `AvatarMessage.java` | Add `Animation` record variant |
| `MessageCodec.java` | Add encode/decode for `Animation` |
| `MessageCodecTest.java` | Add round-trip test for `Animation` |

### 9.2 speech-demo (static resources + HTML)

| File | Change |
|------|--------|
| `src/main/resources/META-INF/resources/animations/*.fbx` | ~15-20 Mixamo FBX files |
| `src/main/resources/META-INF/resources/index.html` | Replace `playGesture` calls with animation system: `ANIMATIONS` config, classifier, idle loop, animation picker UI, server-side animation message handler |

## 10. Testing

### 10.1 Automated (JUnit)

| Test | What it verifies |
|------|-----------------|
| `MessageCodecTest` — Animation round-trip | `Animation` message JSON encode/decode, nullable fields |

### 10.2 Manual (browser)

| Test | What it verifies |
|------|-----------------|
| Type "hello" → avatar waves | Greeting keyword match → waving animation |
| Type a question → avatar thinks while generating | Thinking animation on processing start |
| Long response → explaining gestures while speaking | Layered animation + lip-sync |
| Wave animation → mouth stays closed | Exclusive flag suppresses visemes |
| Idle for 8+ seconds → avatar fidgets | Idle timeout loop |
| Animation picker → select + play | Manual trigger from UI |
| Switch avatar model → animations still work | Mixamo rig compatibility across GLB models |

## 11. Scope Boundary

**In scope:**
- Mixamo FBX animation files (sourced, named, bundled)
- `AvatarMessage.Animation` protocol extension (speech-ws)
- Client-side keyword classifier replacing `playGesture` calls
- Context-dependent layering (exclusive flag)
- Animation picker UI
- Idle animation loop
- Thinking animation during LLM processing

**Out of scope:**
- Server-side cognition emitting animation cues (Tier 2 — the protocol is ready)
- LLM system prompt modifications for [ANIM:] tags
- Animation blending/crossfading (TalkingHead handles transitions internally)
- Custom animation upload
- Per-avatar animation compatibility testing beyond the bundled models

## References

- [TalkingHead v1.8](https://github.com/met4citizen/TalkingHead) — `playAnimation(url, onprogress, dur, ndx, scale)` API
- [Mixamo](https://www.mixamo.com/) — animation source (Adobe, royalty-free)
- speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java — existing protocol
- speech-demo/src/main/resources/META-INF/resources/index.html — current animation code (lines 184-189)
- specs/issue-194-talking-avatar-tier1/2026-08-27-avatar-web-component-design.md — original avatar architecture

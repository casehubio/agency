# Mixamo Avatar Body Animation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #450 — Richer avatar body animation via Mixamo FBX
**Issue group:** #450

**Goal:** Add expressive full-body Mixamo FBX animations to the speech demo avatar, triggered contextually by response content with server-side animation cue support.

**Architecture:** TalkingHead v1.8's built-in `playAnimation(url)` loads Mixamo FBX files directly. A client-side keyword classifier maps response text to animation categories. A new `AvatarMessage.Animation` WebSocket message type enables server-side animation cues. Body animations layer with lip-sync visemes by default; full-body animations use an `exclusive` flag to suppress visemes.

**Tech Stack:** Java 17 (speech-ws protocol), JavaScript (speech-demo index.html), TalkingHead v1.8, Mixamo FBX, Three.js FBXLoader

## Global Constraints

- speech-ws targets Java 17 (no FFM)
- speech-demo is a Quarkus app; static resources go in `src/main/resources/META-INF/resources/`
- TalkingHead loaded from CDN: `https://cdn.jsdelivr.net/gh/met4citizen/TalkingHead@1.8/modules/talkinghead.mjs`
- Mixamo FBX files must be downloaded "Without Skin" and "In Place"
- Protocol messages use gson (existing dependency)
- Tests use JUnit 5 + AssertJ (existing pattern)

---

## Batch 1: Protocol Extension

### Task 1: Add AvatarMessage.Animation and MessageCodec support

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java`
- Modify: `speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java`

**Interfaces:**
- Consumes: existing `AvatarMessage` sealed interface, `MessageCodec` encode/decode pattern
- Produces: `AvatarMessage.Animation(String category, @Nullable String url, @Nullable Integer durationSec)` — server→client animation cue

- [ ] **Step 1: Write failing tests for Animation message encode/decode**

Add three tests to `MessageCodecTest.java`:

```java
@Test
void encodesAnimationMessage() {
    String json = MessageCodec.encode(new AvatarMessage.Animation("thinking", null, null));
    var obj = JsonParser.parseString(json).getAsJsonObject();
    assertThat(obj.get("type").getAsString()).isEqualTo("animation");
    assertThat(obj.get("category").getAsString()).isEqualTo("thinking");
    assertThat(obj.has("url")).isFalse();
    assertThat(obj.has("durationSec")).isFalse();
}

@Test
void encodesAnimationMessageWithAllFields() {
    String json = MessageCodec.encode(
            new AvatarMessage.Animation("greeting", "animations/wave.fbx", 5));
    var obj = JsonParser.parseString(json).getAsJsonObject();
    assertThat(obj.get("type").getAsString()).isEqualTo("animation");
    assertThat(obj.get("category").getAsString()).isEqualTo("greeting");
    assertThat(obj.get("url").getAsString()).isEqualTo("animations/wave.fbx");
    assertThat(obj.get("durationSec").getAsInt()).isEqualTo(5);
}

@Test
void encodesAnimationMessageWithUrlOnly() {
    String json = MessageCodec.encode(
            new AvatarMessage.Animation("custom", "animations/custom.fbx", null));
    var obj = JsonParser.parseString(json).getAsJsonObject();
    assertThat(obj.get("category").getAsString()).isEqualTo("custom");
    assertThat(obj.get("url").getAsString()).isEqualTo("animations/custom.fbx");
    assertThat(obj.has("durationSec")).isFalse();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl speech-ws -Dtest=MessageCodecTest -DfailIfNoTests=false --batch-mode`
Expected: compilation error — `Animation` record does not exist yet.

- [ ] **Step 3: Add Animation record to AvatarMessage**

Add to `AvatarMessage.java` after the `SpeakerIdentified` record:

```java
record Animation(String category, @org.jspecify.annotations.Nullable String url,
                 @org.jspecify.annotations.Nullable Integer durationSec) implements AvatarMessage {}
```

- [ ] **Step 4: Add encode case to MessageCodec**

Add a new case in the `encode` switch in `MessageCodec.java`, after the `SpeakerIdentified` case:

```java
case AvatarMessage.Animation a -> {
    obj.addProperty("type", "animation");
    obj.addProperty("category", a.category());
    if (a.url() != null) { obj.addProperty("url", a.url()); }
    if (a.durationSec() != null) { obj.addProperty("durationSec", a.durationSec()); }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl speech-ws -Dtest=MessageCodecTest --batch-mode`
Expected: all tests PASS (including the 3 new ones).

- [ ] **Step 6: Run full speech-ws test suite**

Run: `mvn test -pl speech-ws --batch-mode`
Expected: all tests PASS. The sealed interface switch in `encode` is now exhaustive.

- [ ] **Step 7: Commit**

```bash
git add speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java
git add speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java
git add speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java
git commit -m "feat(#450): add AvatarMessage.Animation protocol for body animation cues

Refs #450"
```

---

## Batch 2: Mixamo Animation Resources

### Task 2: Source and bundle Mixamo FBX animations

**Files:**
- Create: `speech-demo/src/main/resources/META-INF/resources/animations/` (directory)
- Create: ~15-20 `.fbx` files in that directory

**Interfaces:**
- Consumes: nothing
- Produces: static FBX files served at `/animations/<name>.fbx` by Quarkus

This task requires manual Mixamo downloads. The implementer must have an Adobe account (free).

- [ ] **Step 1: Create the animations directory**

```bash
mkdir -p speech-demo/src/main/resources/META-INF/resources/animations
```

- [ ] **Step 2: Download animations from mixamo.com**

Go to https://www.mixamo.com/. For each animation below, search by name, select the first result, configure "Without Skin" format, check "In Place", and download FBX.

| Category | Mixamo Search Term | Save As | Exclusive |
|----------|-------------------|---------|-----------|
| thinking | "Thinking" | `thinking-1.fbx` | false |
| thinking | "Chin Touch" (or "Weight Shift While Looking") | `thinking-2.fbx` | false |
| explaining | "Talking" | `explaining-1.fbx` | false |
| explaining | "Gesturing" (or "Explaining") | `explaining-2.fbx` | false |
| agreement | "Agreeing" (or "Nod Yes") | `agreement-1.fbx` | false |
| greeting | "Waving" | `greeting-wave.fbx` | true |
| greeting | "Happy Hand Wave" | `greeting-casual.fbx` | true |
| surprise | "Surprise" | `surprise-1.fbx` | false |
| confusion | "Confused" (or "Head Scratch") | `confusion-1.fbx` | false |
| confusion | "Shrug" | `confusion-shrug.fbx` | false |
| happy | "Laughing" | `happy-laugh.fbx` | false |
| happy | "Clapping" | `happy-clap.fbx` | true |
| sad | "Sad Idle" (or "Disappointed") | `sad-1.fbx` | false |
| defensive | "Arms Crossed" (or "Arm Cross Standing") | `defensive-1.fbx` | false |
| pointing | "Pointing" | `pointing-1.fbx` | true |
| idle | "Idle" (or "Standing Idle") | `idle-1.fbx` | false |
| idle | "Weight Shift" | `idle-2.fbx` | false |
| idle | "Looking Around" | `idle-3.fbx` | false |

If a specific search term yields no results, find the closest equivalent. The exact animation matters less than having one per category.

- [ ] **Step 3: Verify file sizes**

Each FBX should be 50-500KB. If any exceed 1MB, re-download with "Without Skin" checked — the mesh was included.

- [ ] **Step 4: Commit**

```bash
git add speech-demo/src/main/resources/META-INF/resources/animations/
git commit -m "feat(#450): add curated Mixamo FBX animation set

18 animations across 11 categories: thinking, explaining, agreement,
greeting, surprise, confusion, happy, sad, defensive, pointing, idle.
Downloaded 'Without Skin' + 'In Place' from mixamo.com.

Refs #450"
```

---

## Batch 3: Client-Side Animation System

### Task 3: Replace playGesture with Mixamo animation system in index.html

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/resources/index.html`

**Interfaces:**
- Consumes: TalkingHead `playAnimation(url, onprogress, dur, ndx, scale)` and `stopAnimation()` APIs; FBX files from Task 2; `AvatarMessage.Animation` protocol from Task 1
- Produces: fully functional animation system in the demo page

- [ ] **Step 1: Add the ANIMATIONS config object**

Add after the `let visemeMeshes = null;` line (around line 150) in the `<script>` block:

```javascript
var ANIMATIONS = {
  greeting: {
    keywords: ["hello", "hi ", "hey ", "welcome", "good morning", "good afternoon"],
    anims: [
      { url: "animations/greeting-wave.fbx", dur: 3, exclusive: true },
      { url: "animations/greeting-casual.fbx", dur: 3, exclusive: true }
    ]
  },
  thinking: {
    keywords: ["let me think", "hmm", "consider", "wondering", "perhaps", "let me see"],
    anims: [
      { url: "animations/thinking-1.fbx", dur: 4, exclusive: false },
      { url: "animations/thinking-2.fbx", dur: 4, exclusive: false }
    ]
  },
  explaining: {
    keywords: ["here's how", "the reason", "because", "essentially", "basically", "in other words", "what this means"],
    anims: [
      { url: "animations/explaining-1.fbx", dur: 5, exclusive: false },
      { url: "animations/explaining-2.fbx", dur: 5, exclusive: false }
    ]
  },
  agreement: {
    keywords: ["yes", "right", "exactly", "agree", "correct", "absolutely", "indeed"],
    anims: [
      { url: "animations/agreement-1.fbx", dur: 3, exclusive: false }
    ]
  },
  surprise: {
    keywords: ["wow", "really", "amazing", "incredible", "fascinating", "remarkable"],
    anims: [
      { url: "animations/surprise-1.fbx", dur: 3, exclusive: false }
    ]
  },
  confusion: {
    keywords: ["not sure", "i don't know", "unclear", "hard to say", "it depends"],
    anims: [
      { url: "animations/confusion-1.fbx", dur: 3, exclusive: false },
      { url: "animations/confusion-shrug.fbx", dur: 3, exclusive: false }
    ]
  },
  happy: {
    keywords: ["great", "wonderful", "fantastic", "excellent", "haha", "that's funny"],
    anims: [
      { url: "animations/happy-laugh.fbx", dur: 3, exclusive: false },
      { url: "animations/happy-clap.fbx", dur: 3, exclusive: true }
    ]
  },
  sad: {
    keywords: ["sorry", "unfortunately", "sadly", "too bad", "regret"],
    anims: [
      { url: "animations/sad-1.fbx", dur: 4, exclusive: false }
    ]
  },
  defensive: {
    keywords: ["however", "on the other hand", "but ", "disagree", "not necessarily"],
    anims: [
      { url: "animations/defensive-1.fbx", dur: 4, exclusive: false }
    ]
  },
  pointing: {
    keywords: ["look at", "right here", "this one", "over there", "check this"],
    anims: [
      { url: "animations/pointing-1.fbx", dur: 3, exclusive: true }
    ]
  },
  idle: {
    keywords: [],
    anims: [
      { url: "animations/idle-1.fbx", dur: 8, exclusive: false },
      { url: "animations/idle-2.fbx", dur: 8, exclusive: false },
      { url: "animations/idle-3.fbx", dur: 10, exclusive: false }
    ]
  }
};
```

- [ ] **Step 2: Add the classifier and playback functions**

Add after the `ANIMATIONS` config:

```javascript
var currentAnimation = null;
var animationExclusive = false;
var idleTimer = null;
var serverAnimationReceived = false;

function classifyResponse(text) {
  var lower = text.toLowerCase();
  for (var category in ANIMATIONS) {
    var config = ANIMATIONS[category];
    if (config.keywords.length === 0) continue;
    for (var i = 0; i < config.keywords.length; i++) {
      if (lower.includes(config.keywords[i])) return category;
    }
  }
  return null;
}

function playAnimationByCategory(category) {
  if (!head) return;
  var config = ANIMATIONS[category];
  if (!config) return;
  var anim = config.anims[Math.floor(Math.random() * config.anims.length)];
  animationExclusive = anim.exclusive;
  currentAnimation = category;
  head.playAnimation(anim.url, null, anim.dur).catch(function(e) {
    console.warn('Animation load failed:', e);
    animationExclusive = false;
    currentAnimation = null;
  });
  resetIdleTimer();
}

function stopCurrentAnimation() {
  if (head && currentAnimation) {
    head.stopAnimation();
    animationExclusive = false;
    currentAnimation = null;
  }
}

function resetIdleTimer() {
  clearTimeout(idleTimer);
  idleTimer = setTimeout(function() {
    if (head && !isPlaying && !currentAnimation) {
      playAnimationByCategory('idle');
    }
  }, 5000 + Math.random() * 3000);
}
```

- [ ] **Step 3: Replace the existing playGesture code in the response handler**

Find the existing response handler in `ws.onmessage` (around line 183-189):

Replace:
```javascript
        case 'response':
          addMessage(msg.text, 'avatar');
          if (head) {
            head.setMood('happy');
            var text = msg.text.toLowerCase();
            if (text.includes('?') || text.includes('not sure')) head.playGesture('shrug', 2);
            else if (text.includes('hello') || text.includes('hi') || text.includes('hey')) head.playGesture('handup', 2);
            else if (text.includes('great') || text.includes('yes') || text.includes('right')) head.playGesture('thumbup', 2);
            setTimeout(function() { if (head) head.setMood('neutral'); }, 5000);
          }
          break;
```

With:
```javascript
        case 'response':
          addMessage(msg.text, 'avatar');
          if (head) {
            if (!serverAnimationReceived) {
              var category = classifyResponse(msg.text);
              if (category) playAnimationByCategory(category);
            }
            serverAnimationReceived = false;
          }
          resetIdleTimer();
          break;
```

- [ ] **Step 4: Add server-side animation message handler**

Add a new case in the `ws.onmessage` switch, before the `case 'error':` line:

```javascript
        case 'animation':
          serverAnimationReceived = true;
          if (msg.url) {
            var dur = msg.durationSec || 5;
            animationExclusive = false;
            head.playAnimation(msg.url, null, dur).catch(function(e) {
              console.warn('Server animation failed:', e);
            });
          } else if (msg.category) {
            playAnimationByCategory(msg.category);
          }
          break;
```

- [ ] **Step 5: Add thinking animation on message send**

In the `sendText()` function, add after `status.textContent = 'Processing...';`:

```javascript
  if (head) playAnimationByCategory('thinking');
```

In the `stopRecording()` function, add after `status.textContent = 'Processing speech...';` (around line 411):

```javascript
  if (head) playAnimationByCategory('thinking');
```

- [ ] **Step 6: Modify viseme driving to respect exclusive flag**

In the `animate()` function inside `playNextQueued()` (around line 309), wrap the viseme update in an exclusive guard. Replace:

```javascript
    meshes.forEach(function(m) {
      for (var k in m.dict) {
        var idx = m.dict[k];
        var target = (k === 'viseme_' + activeViseme) ? activeWeight : 0;
        var current = m.mesh.morphTargetInfluences[idx] || 0;
        var lerp = target > current ? ATTACK : DECAY;
        m.mesh.morphTargetInfluences[idx] = current + (target - current) * lerp;
      }
    });
```

With:

```javascript
    if (!animationExclusive) {
      meshes.forEach(function(m) {
        for (var k in m.dict) {
          var idx = m.dict[k];
          var target = (k === 'viseme_' + activeViseme) ? activeWeight : 0;
          var current = m.mesh.morphTargetInfluences[idx] || 0;
          var lerp = target > current ? ATTACK : DECAY;
          m.mesh.morphTargetInfluences[idx] = current + (target - current) * lerp;
        }
      });
    }
```

- [ ] **Step 7: Add animation picker UI**

In the `#controls` div (around line 54), add after the Avatar select label:

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
  <button id="playAnim" style="padding:0.3rem 0.6rem;border-radius:6px;border:1px solid #555;background:#2a2a3e;color:#e0e0e0;cursor:pointer;font-size:0.85rem" title="Play selected animation">▶</button>
```

Add the event listener at the end of the `<script>` block, before `</script>`:

```javascript
document.getElementById('playAnim').addEventListener('click', function() {
  var category = document.getElementById('animPicker').value;
  if (category && head) {
    playAnimationByCategory(category);
    addMessage('Playing animation: ' + category, 'system');
  }
});
```

- [ ] **Step 8: Start idle timer when avatar loads**

In the `toggleAvatar` click handler, after `head.playGesture('handup', 3);` (around line 542), add:

```javascript
      resetIdleTimer();
```

- [ ] **Step 9: Build and verify**

Run: `mvn install --batch-mode -DskipTests`
Expected: BUILD SUCCESS — static resources bundled, no compilation errors.

Run: `mvn test --batch-mode`
Expected: all tests PASS.

- [ ] **Step 10: Manual browser verification**

Start the demo: `mvn quarkus:dev -pl speech-demo`

Test matrix:
1. Show 3D Avatar → avatar loads
2. Type "hello there" → avatar plays waving animation
3. Type a question → avatar plays thinking animation while LLM generates
4. Receive long explanation → explaining gesture layers with lip-sync
5. Wait 8+ seconds → idle animation plays
6. Animation picker → select "surprise" → click ▶ → surprise animation plays
7. Switch avatar model → animations still work with different GLB

- [ ] **Step 11: Commit**

```bash
git add speech-demo/src/main/resources/META-INF/resources/index.html
git commit -m "feat(#450): add Mixamo body animation system to speech demo

Replace 3 playGesture calls with full animation system:
- 11 animation categories with keyword classifier
- Context-dependent layering (exclusive flag for full-body)
- Server-side animation cue handler (AvatarMessage.Animation)
- Idle animation loop with jitter
- Animation picker UI for manual triggering
- Thinking animation during LLM processing

Refs #450"
```

---

## References

- [2026-11-11-mixamo-avatar-animation-design.md] — design spec this plan implements
- [speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java] — existing protocol sealed interface
- [speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java] — existing encode/decode pattern
- [speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java] — existing test pattern
- [speech-demo/src/main/resources/META-INF/resources/index.html] — existing demo page
- [TalkingHead v1.8](https://github.com/met4citizen/TalkingHead) — playAnimation API documentation
- [Mixamo](https://www.mixamo.com/) — animation source
- [GitHub #450](https://github.com/casehubio/agency/issues/450) — focal issue

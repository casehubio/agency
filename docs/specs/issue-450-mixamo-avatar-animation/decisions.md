# Decisions — Issue #450: Mixamo Avatar Animation

## D1: Animation trigger side

**Choice:** Hybrid — client-side keyword heuristics as default, with server-side override via a new `AvatarMessage.Animation` WebSocket message type
**Alternatives:**
- Client-side only — simpler but no path for cognition-driven animation
- Server-side LLM tagging — more accurate but heavier, adds latency and prompt complexity
**Rationale:** Client-side heuristics cover 90% of conversational contexts at zero latency cost. The server-side message type costs almost nothing to add now and future-proofs for cognition integration (Tier 2) without a protocol change later.
**Trade-offs:** Two animation trigger paths to maintain; potential for client and server cues conflicting (server wins).
**Sources:** speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java, speech-demo index.html
**Exploration:** quick
**Status:** captured

## D2: Animation file delivery

**Choice:** Static resources bundled in `speech-demo/src/main/resources/META-INF/resources/animations/`
**Alternatives:**
- Provisioner auto-download — keeps repo smaller but adds first-run latency and complexity
- CDN-hosted — zero repo impact but requires internet, adds per-animation latency
**Rationale:** ~15-20 FBX files at 100-500KB each = 2-5MB total. Trivial for a demo module. Always available, no download delay after initial page load.
**Trade-offs:** Slightly larger speech-demo module size. Binary FBX files in git.
**Sources:** speech-demo/pom.xml, existing avatar GLB pattern
**Exploration:** quick
**Status:** captured

## D3: Animation scope

**Choice:** Extended expressive — ~15-20 animations covering: thinking (chin touch, looking up), explaining (hand gestures), agreement (nod), greeting (wave), idle variations (weight shift, look around), surprise, shrug, laughing, clapping, pointing, confused head scratch, arms crossed (defensive), excited, sad/disappointed
**Alternatives:**
- Core conversational (~8-10) — covers 90% but less personality range
- Minimal proof-of-concept (~4-5) — proves pipeline but underdelivers on demo impact
**Rationale:** The extended set gives the avatar genuine personality range. Each animation is a small FBX file, so the marginal cost of more is low. The visual impact of a varied animation repertoire is disproportionately high.
**Trade-offs:** More animations to source, name, and tune keyword mappings for. Slightly larger resource footprint.
**Sources:** Mixamo animation library, TalkingHead playAnimation API
**Exploration:** quick
**Status:** captured

## D4: Animation layering

**Choice:** Context-dependent — layer body animations with lip-sync by default, with per-animation `exclusive` flag for full-body animations that should suppress visemes
**Alternatives:**
- Always layer — simpler but waving while talking looks odd
- Sequential only — animations only between speech, less expressive
**Rationale:** Body animations and lip-sync operate on different targets (bone transforms vs morph target blend shapes), so layering is the natural default. Full-body animations like jumping or waving are the exception — they need an `exclusive` flag to temporarily suppress viseme driving.
**Trade-offs:** Per-animation configuration required. Slightly more complex playback logic.
**Sources:** TalkingHead playAnimation (armature) vs viseme morphTargetInfluences (separate systems)
**Exploration:** quick
**Status:** captured

## D5: WebSocket protocol extension

**Choice:** Add `AvatarMessage.Animation` record now, wire it end-to-end
**Alternatives:**
- Defer until cognition integration needs it — less code now but requires a second protocol touch later
**Rationale:** The incremental cost of adding a record variant to the sealed interface is near zero. Having it wired means cognition integration (Tier 2) can send animation cues without protocol changes.
**Trade-offs:** Slightly more code in protocol layer for a feature that cognition doesn't use yet.
**Sources:** speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java
**Depends on:** D1 (hybrid trigger side)
**Exploration:** quick
**Status:** captured

## D6: UI controls

**Choice:** Animation picker — dropdown or buttons to manually trigger specific animations
**Alternatives:**
- No controls — automatic only, cleaner UI
- Toggle checkbox — on/off switch, minimal UI footprint
**Rationale:** A picker lets users demo the full animation range directly, which is valuable for a demo app. It also helps during development and debugging.
**Trade-offs:** Adds UI clutter. Need to keep picker in sync with available animations.
**Sources:** speech-demo index.html existing controls pattern
**Exploration:** quick
**Status:** captured

## D7: Client-side classification approach

**Choice:** Flat keyword map — JS object mapping categories to keyword lists and animation URL pools. First match wins, random animation from pool.
**Alternatives:**
- Weighted multi-match — keywords with scores, top-N categories trigger sequential animations. More nuanced but harder to tune.
- LLM-classified suffix — LLM emits [ANIM:category] tags. Most accurate but adds latency and prompt complexity.
**Rationale:** A flat keyword map is trivially extensible (add an entry = add an animation), easy to debug (console.log the matched category), and sufficient for conversational context classification. The server-side `AvatarMessage.Animation` override handles edge cases.
**Trade-offs:** Keyword matching is imprecise — "I'm not sure" matches "not sure" but misses sarcasm or nuance. Acceptable for a demo.
**Depends on:** D1 (hybrid trigger side)
**Sources:** Existing playGesture keyword matching in index.html
**Exploration:** quick
**Status:** captured

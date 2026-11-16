---
entry_type: note
subtype: diary
title: "The body language problem"
date: 2026-11-11
author: mdp
tags: [avatar, animation, mixamo, mesh2motion, cc0, retargeting, talkinghead, body-language]
series: speech-pipeline
refs:
  - casehubio/agency#450
  - casehubio/agency#455
---

A 3D avatar that talks with lip-synced speech but stands perfectly still looks like a hostage reading a ransom note. The speech pipeline works — STT, LLM, TTS, viseme timing — but the body does nothing. Three `playGesture('shrug', 2)` calls in the demo were the extent of physical expression. Time to fix that.

## The deceptively simple plan

TalkingHead v1.8 has a built-in `playAnimation(url)` method. It loads Mixamo FBX files, auto-strips the `mixamorig` bone prefix, caches clips, handles the 100:1 scale. Mixamo has 2,000+ free mocap animations. Download some, trigger them from the LLM response content, done.

The trigger architecture ended up as a hybrid: a client-side keyword classifier picks an animation category from the response text ("hello" → greeting, "let me think" → thinking, "unfortunately" → sad), and a server-side `AvatarMessage.Animation` WebSocket message lets the cognition layer override with explicit cues. The keyword classifier handles 90% of cases at zero latency cost. The server path is ready for when personality and mood should influence which gesture plays.

That part worked. I had a thinking animation playing while the LLM generated, a wave on greeting, explaining gestures during long responses. The demo came alive. Then the distribution question killed it.

## Mixamo is a trap

Mixamo is free to use. It is not free to distribute. You need an Adobe account to download. The redistribution terms are unclear at best. And asking end users to create an Adobe account, manually search for 15 specific animations, download each one individually with "Without Skin" checked — that is not a viable developer experience for an open-source project.

I spent time automating the downloads via Playwright — searching Mixamo, clicking through the download dialogs, even automating the "Without Skin" export setting. All of which works. None of which solves the fact that the files cannot ship in a repository. Mixamo is useful as a prototyping tool to figure out what animations you need. It is not a source for distributable assets.

## The open-source animation landscape

The search for CC0 alternatives turned up more than expected.

**Mesh2Motion** (mesh2motion.org) is the standout. Open-source Mixamo alternative — 178 hand-keyed CC0 animations, MIT code, Blender source files on GitHub. It covers humans, foxes, dragons, spiders, horses. The human library has emotes (greeting, head nod, clapping, yes), emotions (angry, confused, reject, victory), idles (talking, listening, fold arms, subtle, shakeoff), and activities (interact, meditate, salute). About 80% coverage of what a conversational avatar needs.

**Quaternius** offers 130+ game animations, also CC0. Game-oriented — lots of locomotion and combat, fewer conversational gestures. Useful as a supplement.

**CMU Motion Capture Database** has 2,500+ clips, free for all uses. Mocap quality — sometimes jittery, often needs cleanup. RancidMilk already converted the full set to FBX with a Quaternius CC0 rig.

**UniMate** (Princeton/Berkeley/MIT, SIGGRAPH Asia 2026) does something different. It generates animations from text prompts on arbitrary skeletons in real time. Feed it "An object waves hello" and it produces joint rotations — no clip library, no retargeting. The code is MIT. The model checkpoints are CC BY-NC, but that only restricts distributing the weights themselves — the generated animations are yours. The right use of UniMate is not as a runtime engine but as an offline motion factory: generate a curated library of 50–100 animations with rich semantic metadata, then ship those.

## The retargeting wall

Here is where the day went sideways.

Mesh2Motion animations use their own bone naming (`pelvis`, `clavicle_l`, `upperarm_l`). Our avatar uses Mixamo naming (`Hips`, `LeftShoulder`, `LeftArm`). The names map 1:1 — a rename table covers it. But the bone *orientations* do not match.

An animation stores rotations relative to a rest pose. When the rest pose is different between the source and target skeletons, the rotations produce wrong results. Specifically: arms that should extend forward instead twist behind the back. Elbows bend backward. The avatar looks like a contortionist.

Mesh2Motion has a "Use Your Rigged Model" retarget tool. Upload your avatar, it auto-detects the Mixamo rig, maps all 67 bones. The Auto-Map even labels it with a "✨Mixamo" tag. But preview the animation and the arms are contorted. The tool maps bone names correctly but does not correct for the orientation difference.

I tried:

- **Blender batch renaming** — rename bones in the deformation skeleton, export FBX. Names matched, but Blender's FBX export lost coordinate space alignment. Avatar displaced off-screen.

- **Manual rotation correction in Blender** — compute the quaternion delta between source and target rest poses, apply to every keyframe. Tried `target * source^-1`, `target^-1 * source`, and sandwich transforms. Each changed the distortion character without fixing it. The math is not a simple global correction — it depends on parent-relative transforms and accumulated chain errors.

- **`@three-ws/retarget` library** — an npm package that does proper retargeting with rest-pose and hip-axis correction. The math is right (world-space `q' = L · q · R` where L/R derive from rest-pose differences). But applying it on top of Mesh2Motion's already-partially-retargeted output produces double corrections.

The root issue is that proper retargeting between different skeletal conventions is a solved problem — Unreal Engine, Unity, Auto-Rig Pro all handle it — but the open-source browser pipeline (GLB/FBX → Three.js → TalkingHead) does not have a turnkey solution yet.

## The taxonomy that matters more than the code

The animation work surfaced a design question more important than the retargeting bug.

A flat list of animations — greeting, thinking, explaining — is insufficient. Body language carries emotional register. The same "agreement" category needs at least three variants: an enthusiastic nod (strong endorsement), a neutral confirmation (factual acknowledgement), and a reluctant concession (backing down when pressed). Play the wrong one and the avatar appears passive-aggressive. A chirpy wave is perfect for a children's tutor and completely wrong for a clinical assistant.

The motion base needs to be a **tagged library** — each animation indexed by category, sub-type, register, and context:

```yaml
greeting-wave-warm:
  register: warm
  context: [returning-user, friendly, general]

greeting-nod-professional:
  register: composed
  context: [business, formal, clinical]

agreement-reluctant:
  register: composed
  context: [backing-down, accepting-when-pressed]
```

Selection then becomes: **category** (from keyword classifier or LLM tag) × **register** (from avatar personality config) × **context** (from conversation state). The keyword classifier picks the category. The personality layer picks the register. The cognition system — when it arrives — overrides both.

This is why UniMate as an offline factory is attractive. Instead of searching through existing libraries hoping to find a "reluctant agreement" animation, you generate one from the description. The prompt *is* the metadata.

## Where this lands

The protocol and classifier are shipped — `AvatarMessage.Animation`, keyword heuristics, idle loop, animation picker, context-dependent layering with lip-sync. The architecture is right.

The CC0 motion base is not shipped. It depends on either Mesh2Motion fixing their Mixamo retarget (issue filed), or generating animations via UniMate on the Mixamo skeleton directly. Both are active paths. The taxonomy of what we need — 23 animations across 11 categories with emotional register variants — is defined.

The broader point: conversational AI avatars need body language, and body language needs open-source animation libraries purpose-built for conversation, not repurposed from game asset packs. The game industry has solved character animation. The conversational AI industry has not yet caught up. Mesh2Motion, UniMate, and projects like them are the beginning of that catch-up. The tooling is almost there — the retargeting gap between "works in the tool's preview" and "works on your specific avatar" is the last mile.

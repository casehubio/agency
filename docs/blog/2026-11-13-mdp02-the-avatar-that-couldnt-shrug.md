---
layout: post
title: "The avatar that couldn't shrug"
date: 2026-11-13
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [animation, avatar, talkinghead, body-language, three.js, taxonomy]
series: issue-455-cc0-motion-base-tagged-animation-library
---

# The avatar that couldn't shrug

TalkingHead is a good face. Lip sync from visemes, automatic blinks, mood-driven expressions, procedural eye gaze that tracks the camera and occasionally glances away. It does subtle body sway — a breathing idle that sells "this character is present." For a talking-head demo, that's enough.

For a conversational avatar, it isn't. The body stands there like a mannequin between the neck and the floor. The avatar can express "happy" with a smile but can't clap. It can look confused with a furrowed brow but can't shrug. It can speak with lip sync but can't gesture with its hands. Seventy percent of human communication is nonverbal, and our avatar had access to about five percent of that range.

## 52 bodies for a face

The previous session got a retarget algorithm working — `PoseAlignedRetargeter` can take any humanoid animation and replay it on any humanoid avatar regardless of rest pose or skeleton proportions. The M2M animation library has 234 CC0 clips. Claude batch-retargeted 52 of them onto our RPM avatar in one pass: conversational reactions, idle poses, dances, dramatic gestures.

The integration problem is layering. TalkingHead owns the head and neck bones — it writes quaternions to them every frame for gaze tracking and procedural movement. A body animation system that also writes to those bones creates a fight. The solution: strip Head, Neck, and Eye tracks from every body animation clip at load time, and run a separate `AnimationMixer` on the avatar's armature. TalkingHead keeps the face. The body system keeps everything below the neck. They share the same skeleton but never write to the same bones.

```
TalkingHead:  Head, Neck, Eyes  →  face expressions, gaze, lip sync
BodyAnim:     Spine, Arms, Legs →  gestures, idle poses, reactions
                                   (separate AnimationMixer, crossfade)
```

The crossfade is important. Snapping between a folded-arms idle and a victory pose looks robotic. THREE.js `AnimationMixer` handles this — `crossFadeTo()` blends over 0.4 seconds, and the transition reads as a natural weight shift rather than a state change.

## The taxonomy nobody starts with

The first version was a flat list. Fifty-two clips in a weighted pool, random selection, crossfade. It worked — the avatar moved — but it raised an immediate question: should a moonwalk play while the avatar is mid-sentence about database architecture?

The answer is obviously no, which means the system needs to know the difference between "talking" and "waiting." That's two pools: a speaking pool (conversational stances, hand gestures, agreement nods) and an idle pool (broader variety, personality animations, occasional dances). But even within the speaking pool, "emphatic agreement" and "reluctant agreement" are not the same gesture. A nod and a clap both say "yes" but at different intensities.

This pushed toward a three-level taxonomy:

```
category → subcategory → clips (with weights)

conversational
  ├── stance        idle-subtle(10), idle-foldarms(6), idle-listening(8)
  ├── hand-gesture  idle-talking(12)
  ├── agreement     head-nod(8), yes(6), clapping(3)
  ├── disagreement  reject(8), push(5)
  ├── emphasis      chest-open(6), knock(4)
  └── uncertainty   confused(8)
```

The speaking pool draws from subcategories, not individual clips. `[gesture:agreement]` picks randomly from agreement variants. Right now most subcategories have one or two clips. The structure is designed for three to five variants each — enough that the avatar never does the same gesture twice in a row for the same emotion.

## The gestures that don't exist

This is where it got interesting. We went looking for conversational body poses and found that the M2M library — 234 animations covering combat, locomotion, emotes, and zombie walks — had almost nothing for the thing humans do most: talk while standing.

The obvious gaps:

- **Hands clasped in front** — the default attentive stance in any conversation
- **Hands behind back** — casual thoughtful pose
- **Hand on chin** — the Thinker, universal signal for "I'm processing this"
- **Head shake** — we had a nod for "yes" but no "no"
- **Shrug** — palms up, shoulders raised, the most recognisable uncertainty gesture in Western culture

Then the subtle ones — the hand movements people cycle through unconsciously during conversation. Hands at stomach level, palms inward like holding a small box. That base position shifts: wider apart with palms tilting up (opening up an idea), coming together with fingertips touching (narrowing to a conclusion), one hand forward then the other (punctuating points). Same base, four variations, each a different conversational beat.

None of these exist in motion capture libraries because they're too subtle to be worth capturing. A backflip is dramatic enough to sell. A 2-centimetre palm tilt while explaining a database schema is not. But it's exactly those micro-transitions — the hand position shift, the weight transfer, the slight chest raise when making an important point — that make an avatar look like a person talking instead of a mannequin occasionally doing a dance move.

The full gap list landed at 23 poses. Five conversational stances, four hand-gesture cycle clips, six reactive gestures, eight emotional body poses. All need to be authored by hand in Blender rather than motion-captured — the movements are too small and too specific to capture reliably.

## What gesture tags open up

The LLM side is straightforward. The response text can contain inline tags: `[gesture:dab]` triggers the dab animation, `[gesture:confused]` triggers confused. The tags are stripped before the text reaches the user's screen. A keyword fallback handles responses without explicit tags — "exactly" triggers an agreement gesture, "wow" triggers surprise.

But the interesting future isn't one-gesture-per-response. It's the LLM embedding gesture tags throughout its speech, the way a person gestures while talking. A shrug mid-sentence. Hands opening up as the explanation broadens. A nod while acknowledging the user's point. The taxonomy supports this — `parseGestureTags()` extracts multiple tags per response, and each resolves through the gesture map to either a specific clip or a random pick from a subcategory.

Getting the LLM to do this well is a system prompt problem, not an animation system problem. The plumbing is there. The question is whether the gesture vocabulary is rich enough — and dense enough in conversational subtlety — to support that kind of continuous body language.

## What this actually is

The 52 clips, the taxonomy, the idle cycle, the gesture tags — they're a body language vocabulary. Not a complete one. Twenty-three of the most important conversational poses don't exist yet. Most subcategories have one variant where they need four. But the architecture handles the scale: adding a new "reluctant agreement" clip is dropping one entry into a JSON array. The picker, the crossfade, the pool selection, the gesture tag resolution all work without changes.

TalkingHead might want this back. The animations are pre-retargeted to RPM's skeleton, and all RPM avatars share the same bone names and rest pose. The body animation module, the taxonomy, the head-bone masking — it's a generic layer that slots onto any TalkingHead instance. The contribution path is straightforward. Whether the TalkingHead maintainer wants body animations in a project named "TalkingHead" is a different question — but the gap is real, and the solution is portable.

The avatar can shrug now. It just doesn't know when to.

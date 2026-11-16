---
layout: post
title: "The retarget that looked right on paper"
date: 2026-11-13
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [animation, retarget, quaternion, avatar, three.js, mesh2motion]
series: issue-455-cc0-motion-base-tagged-animation-library
---

# The retarget that looked right on paper

We spent a week trying to get Mesh2Motion's animation library to play correctly on ReadyPlayerMe avatars. The body and legs came together early. The arms refused.

The bind correction formula is standard retargeting math — `L × Q × R` where L and R are precomputed from the source and target rest poses. I proved algebraically that it preserves world-frame animation deltas exactly. The rest-pose round-trip was perfect. Every diagnostic confirmed the formula was correct. And the arms still stretched too wide on every clapping animation, every crossed-arm pose, every victory celebration.

## The formula was correct. The inputs were wrong.

The M2M skeleton uses T-pose — arms horizontal. The RPM avatar uses A-pose — arms angled 54° below horizontal. When you compute R = Ws⁻¹ × Wt from two rest poses that differ by 54° in arm direction plus 89° in bone roll, R comes out at 103°. That 103° R conjugates every animation delta — rotating the axis of arm motion so far that "swing inward to clap" becomes "swing sideways and stay wide."

I decomposed R into swing and twist components, thinking I could drop the swing and keep the twist. The swing-only R was worse — 42° to 96° direction error for inward motions, versus the 54° constant offset with full R. Dropping R entirely gave perfect world-direction matching but put the arm bones 103° from the mesh's bind pose, which shredded the skin weights.

Three approaches, three failures, all for the same reason: I was trying to fix R after the fact instead of fixing what went into it.

## Align first, then capture

A separate Claude instance (running fable) looked at my diagnostic data and saw what I'd missed. The fix isn't in decomposing R — it's in making the two reference poses match before R is computed.

Walk the target skeleton parent-first. For each bone, swing it so its world-space direction matches the source bone's direction. After this pass, both skeletons have the same bone directions. The offset between them is now pure twist — the legitimate bone-roll convention difference between Blender and Mixamo rigs, around 89° for arm bones. No swing contamination.

Per frame: `target_world = source_world × twist_offset`, converted to local space using the already-retargeted parent. The arms follow the source's world-space trajectory exactly, starting from the target's own rest orientation.

The clapping animation went from arms-wide-apart to hands-meeting-at-chest in one change.

## A positional problem needs a positional fix

The hands still went through each other. RPM has proportionally longer arms relative to shoulder width — reach ratio 4.17 vs M2M's 3.32. Same bone directions, different skeleton geometry: the hands overshoot the body midline by 3.3cm.

I spent another round trying to fix this with rotational corrections — partially misaligning the bone directions to compensate for the position error. The correction was constant, the error varied with the pose, and the result was predictable: some frames better, some worse, never right.

Fable saw this too and built a per-frame IK post-process. For each frame, it computes where the source's palm sits relative to the body, scales that position to the target's proportions, and re-poses the limb with a hinge solve to reach the scaled target. A positional problem, a positional fix. The hands stopped passing through the body.

## What this actually enables

The practical result: any animation from any humanoid library can now play on any avatar, regardless of rest pose or skeleton proportions. M2M's 234 animations — clapping, dancing, combat, idle poses — all work on RPM avatars that were designed for a completely different rig.

For our avatar demo, this means the animation vocabulary went from 24 hand-curated CC0 clips to 234 diverse motions covering idle, conversation, emotion, and play. The avatar can dab when the LLM nails a hard question, shrug when it's uncertain, and drift between idle poses while waiting for input. That's the foundation for a conversational avatar that feels alive rather than looping.

The retarget code is a `PoseAlignedRetargeter` with a `LimbProportionCorrector` — two classes, about 850 lines total, no external dependencies beyond THREE.js. The approach generalises to any pair of humanoid skeletons without per-rig configuration. The PR is open against the Mesh2Motion repo.

The formula I proved correct was correct. It just wasn't the right formula for the problem.

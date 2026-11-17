# M2M Retarget: Bind Correction Fix

## Problem

Mesh2Motion's retarget page produces twisted/contorted arms when exporting animations to Mixamo-rigged avatars (RPM, Avaturn). The swing-twist retarget in `Retargeter.ts` doesn't properly handle the bone roll difference between M2M's skeleton and the target skeleton. Hips converts correctly (chain root where swing-twist works), but child bones accumulate roll error — arms twist behind the back.

Root cause: two compounding issues in the swing-twist retarget:
1. **Missing clavicle chains** — `HumanChainConfig` skips `clavicle_l`/`clavicle_r`, so clavicle rest-pose differences propagate as errors into arm rotations
2. **Swing-twist doesn't capture bone roll** — the fixed model-space swing/twist axes (e.g., `Vec3(1,0,0)` for arm swing) can't represent arbitrary rest-pose roll differences between skeletons

## Fix

Replace the swing-twist bake path in `AnimationRetargetService.retarget_animation_clip()` with per-bone bind correction matrices, applied directly to animation keyframe quaternions.

### Bind Correction Formula

For each mapped bone where source (M2M) and target rest poses differ:

```
Rs = source bone local rest quaternion
Rt = target bone local rest quaternion  
Ws = source bone world rest quaternion (product of all ancestor local rests)
Wt = target bone world rest quaternion

L = Rt × Wt⁻¹ × Ws × Rs⁻¹    (pre-multiply correction)
R = Ws⁻¹ × Wt                  (post-multiply correction)
```

For each quaternion keyframe value `q`:
```
q_corrected = L × q × R
```

For Hips position tracks, rotate by the Hips parent's world quaternion inverse (existing logic, unchanged).

### Where the Change Goes

**File: `src/retarget/AnimationRetargetService.ts`**

The `apply_human_swing_twist_retargeting` method currently:
1. Creates source/target Skeletons and Rigs
2. Builds chain configs from bone mapping
3. Calls `HumanClipRetargeter.retarget()` which runs a frame-by-frame bake loop

Replace with:
1. Capture source skeleton rest poses (local + world) — already available from `RetargetUtils.capture_bone_rest_transforms`
2. Capture target skeleton rest poses (local + world) — already captured at load time in `target_rest_transforms`
3. Compute L/R correction per mapped bone
4. Clone the source clip, rename tracks using bone mapping, apply L/R to quaternion tracks
5. Scale Hips position track using existing `scalar` logic

**New utility: `src/retarget/BindCorrectionRetargeter.ts`**

Static utility class (no state, no frame loop):
- `computeCorrections(sourceRests, targetRests, boneMapping)` → `Map<string, {L, R}>`
- `retarget(clip, corrections, boneMapping, hipScaleFactor)` → `AnimationClip`

### Computing World Rest Quaternions

The source skeleton's bone rest transforms are captured via `RetargetUtils.capture_bone_rest_transforms()` — these give local rest quaternions. World rest quaternions are computed by walking the hierarchy:

```
worldRest[bone] = worldRest[parent] × localRest[bone]
```

For the root bone (pelvis/Hips), `worldRest = poseOffset × localRest`.

The target skeleton world rests come from `RetargetUtils.capture_bind_pose_rest_transforms()` combined with the same hierarchy walk.

### Hips Position Handling

Hip position scaling uses existing logic:
- Source hip height from `Rig.scalar` (source pelvis world Y at rest)
- Target hip height from target skeleton
- Scale factor = `targetHipHeight / sourceHipHeight`
- Apply scale to all position keyframe values

The Hips parent world quaternion correction (for armatures with non-identity transforms) uses existing `root_correction_x_degrees` logic.

### Bone Mapping Integration

The existing `BoneAutoMapper` / `HumanChainConfig.build_mapped_chain_configs()` infrastructure produces the bone mapping (`Map<target, source>`). This is unchanged — bind correction just uses the same mapping to look up which source bone drives which target bone.

### What Stays, What Goes

| Component | Status |
|-----------|--------|
| `BoneAutoMapper`, `BoneChainResolver`, `CanonicalSlotMapper` | Unchanged — bone name mapping |
| `MixamoMapper`, `RigifyMapper` | Unchanged — rig detection |
| `HumanChainConfig` | Unchanged — chain configs still used for bone mapping (not for retarget math) |
| `Retargeter`, `Rig`, `Pose`, `RigItem`, `Joint` | No longer used for the retarget bake path (kept for live preview) |
| `HumanClipRetargeter` | No longer called from `retarget_animation_clip` |
| `RetargetUtils` | Unchanged — rest pose capture utilities |
| `StepExportRetargetedAnimations` | Unchanged — calls `retarget_animation_clip` which now uses bind correction |
| `RetargetAnimationPreview` | Unchanged — live preview can keep swing-twist for now |

### Scope Boundary

This PR fixes the export path only. The live preview on the retarget page still uses swing-twist (it renders frame-by-frame via `requestAnimationFrame`). A separate PR could unify them, but fixing the export is what unblocks the CC0 library.

## Testing

1. Unit test: `BindCorrectionRetargeter` with known source/target rest poses — verify L/R corrections are identity when rests match, non-identity when they differ
2. Integration: export a Greeting animation to a Mixamo-rigged RPM avatar GLB, load in three.js viewer, verify arms wave forward (not behind back)
3. Regression: export with M2M-to-M2M mapping (identical bones) — verify output matches input (identity corrections)

## CC0 Re-export

After the fix lands:
1. Use M2M retarget page with fixed code to export all 220+ animations
2. Export in both GLB and FBX formats
3. Target: RPM/Avaturn Mixamo-rigged avatar (canonical bone names)

## References

- `src/retarget/AnimationRetargetService.ts` — current retarget entry point
- `src/retarget/human-retargeting/Retargeter.ts` — current swing-twist bake
- `src/retarget/human-retargeting/HumanChainConfig.ts` — chain definitions (missing clavicles)
- `src/retarget/RetargetUtils.ts` — rest pose capture utilities
- casehub-agency `retarget.mjs` — proven bind correction implementation
- Mesh2Motion/mesh2motion-app#159 — bug report with rest-pose data

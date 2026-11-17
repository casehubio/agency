# M2M Retarget Bind Correction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** casehubio/agency#455 — CC0 motion base — tagged animation library
**Upstream issue:** Mesh2Motion/mesh2motion-app#159 — Arms contorted when retargeting to Mixamo

**Goal:** Fix M2M's retarget export to handle bone roll differences between source (M2M) and target (Mixamo/RPM) skeletons, then re-export all 220+ CC0 animations in GLB and FBX.

**Architecture:** Replace the swing-twist bake path in `AnimationRetargetService.retarget_animation_clip()` with per-bone L/R bind correction matrices applied directly to animation keyframe quaternions. The existing bone mapping infrastructure (`BoneAutoMapper`, `HumanChainConfig`) is unchanged — only the math that converts rotations between skeleton spaces changes. Hip position scaling uses existing logic.

**Tech Stack:** TypeScript, three.js (Quaternion, Skeleton, AnimationClip), vitest

## Global Constraints

- M2M uses vitest with jsdom — all tests must run without a browser
- three.js Quaternion convention: (x, y, z, w)
- Track name format: `boneName.quaternion` or `boneName.position`
- Bone mapping direction: `Map<targetBoneName, sourceBoneName>`
- PR must pass M2M's existing CI (lint + test)

---

## Batch 1: Bind correction retargeter

### Task 1: BindCorrectionRetargeter — compute corrections and apply to clips

**Files:**
- Create: `src/retarget/human-retargeting/BindCorrectionRetargeter.ts`
- Test: `src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts`

**Interfaces:**
- Consumes: `RetargetUtils.capture_bone_rest_transforms(Skeleton) → BoneRestTransform[]`, `RetargetUtils.reverse_bone_mapping_one_to_one(Map) → Map`
- Produces:
  - `BindCorrectionRetargeter.compute_bind_corrections(source_skeleton: Skeleton, target_skeleton: Skeleton, bone_mapping: Map<string, string>) → Map<string, { L: Quaternion | null, R: Quaternion | null }>`
  - `BindCorrectionRetargeter.retarget(source_clip: AnimationClip, corrections: Map, bone_mapping: Map, options: { hip_scale: number, root_correction_quat: Quaternion | null, position_bone_name: string | null }) → AnimationClip`

- [ ] **Step 1: Write failing test — corrections are identity when skeletons match**

```typescript
import { describe, it, expect } from 'vitest'
import { Bone, Quaternion, Skeleton, Vector3 } from 'three'
import { BindCorrectionRetargeter } from './BindCorrectionRetargeter.ts'

function create_two_bone_skeleton (rootQ: Quaternion, childQ: Quaternion): Skeleton {
  const root = new Bone()
  root.name = 'Root'
  root.quaternion.copy(rootQ)
  const child = new Bone()
  child.name = 'Child'
  child.position.set(0, 1, 0)
  child.quaternion.copy(childQ)
  root.add(child)
  root.updateMatrixWorld(true)
  return new Skeleton([root, child])
}

describe('BindCorrectionRetargeter', () => {
  describe('compute_bind_corrections', () => {
    it('returns empty map when source and target rest poses are identical', () => {
      const q = new Quaternion().setFromAxisAngle(new Vector3(0, 1, 0), Math.PI / 4)
      const cq = new Quaternion().setFromAxisAngle(new Vector3(1, 0, 0), Math.PI / 6)
      const source = create_two_bone_skeleton(q.clone(), cq.clone())
      const target = create_two_bone_skeleton(q.clone(), cq.clone())
      const mapping = new Map([['Root', 'Root'], ['Child', 'Child']])

      const corrections = BindCorrectionRetargeter.compute_bind_corrections(source, target, mapping)

      expect(corrections.size).toBe(0)
    })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /tmp/mesh2motion-app && npx vitest run src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts`
Expected: FAIL — module not found

- [ ] **Step 3: Write BindCorrectionRetargeter with compute_bind_corrections**

```typescript
import {
  type AnimationClip, type Bone, type Skeleton,
  Quaternion, QuaternionKeyframeTrack, Vector3, VectorKeyframeTrack
} from 'three'
import { RetargetUtils, type TrackNameParts } from '../RetargetUtils.ts'

const BIND_EPSILON = 1e-6

interface BindCorrection {
  L: Quaternion | null
  R: Quaternion | null
}

export interface BindCorrectionRetargetOptions {
  hip_scale: number
  root_correction_quat: Quaternion | null
  position_bone_name: string | null
}

export class BindCorrectionRetargeter {
  /**
   * Compute per-bone local and world rest quaternions by walking the skeleton hierarchy.
   * Returns a map of bone name → { local: Quaternion, world: Quaternion }.
   */
  static capture_rest_quaternions (skeleton: Skeleton): Map<string, { local: Quaternion, world: Quaternion }> {
    const bone_index = new Map<Bone, number>()
    skeleton.bones.forEach((bone, i) => { bone_index.set(bone, i) })

    const result = new Map<string, { local: Quaternion, world: Quaternion }>()
    const world_quats: Quaternion[] = []

    for (let i = 0; i < skeleton.bones.length; i++) {
      const bone = skeleton.bones[i]
      const local_q = bone.quaternion.clone()

      let world_q: Quaternion
      if (bone.parent !== null && bone.parent.isBone === true) {
        const parent_idx = bone_index.get(bone.parent as Bone)
        if (parent_idx !== undefined) {
          world_q = world_quats[parent_idx].clone().multiply(local_q)
        } else {
          world_q = local_q.clone()
        }
      } else {
        world_q = local_q.clone()
      }

      world_quats[i] = world_q
      result.set(bone.name, { local: local_q, world: world_q })
    }

    return result
  }

  /**
   * Compute L/R bind correction quaternions for each mapped bone where the
   * source and target rest poses differ.
   *
   * Formula:
   *   L = Rt × Wt⁻¹ × Ws × Rs⁻¹   (pre-multiply correction)
   *   R = Ws⁻¹ × Wt                  (post-multiply correction)
   *
   * Where Rs/Rt are local rest quaternions and Ws/Wt are world rest quaternions.
   *
   * @param source_skeleton M2M skeleton at rest pose
   * @param target_skeleton target skeleton at rest pose
   * @param bone_mapping target bone name → source bone name
   */
  static compute_bind_corrections (
    source_skeleton: Skeleton,
    target_skeleton: Skeleton,
    bone_mapping: Map<string, string>
  ): Map<string, BindCorrection> {
    const source_rests = this.capture_rest_quaternions(source_skeleton)
    const target_rests = this.capture_rest_quaternions(target_skeleton)
    const corrections = new Map<string, BindCorrection>()

    for (const [target_bone_name, source_bone_name] of bone_mapping) {
      const source = source_rests.get(source_bone_name)
      const target = target_rests.get(target_bone_name)
      if (source === undefined || target === undefined) continue

      const Rs = source.local
      const Rt = target.local
      const Ws = source.world
      const Wt = target.world

      // L = Rt × Wt⁻¹ × Ws × Rs⁻¹
      const L = Rt.clone()
        .multiply(Wt.clone().invert())
        .multiply(Ws.clone())
        .multiply(Rs.clone().invert())

      // R = Ws⁻¹ × Wt
      const R = Ws.clone().invert().multiply(Wt.clone())

      const identity_L = (1 - Math.abs(L.w)) < BIND_EPSILON
      const identity_R = (1 - Math.abs(R.w)) < BIND_EPSILON

      if (identity_L && identity_R) continue

      corrections.set(target_bone_name, {
        L: identity_L ? null : L,
        R: identity_R ? null : R
      })
    }

    return corrections
  }

  /**
   * Retarget a source animation clip onto a target skeleton using bind corrections.
   *
   * For each track:
   * - Quaternion tracks: rename bone, apply L/R correction
   * - Position tracks (hips only): rename, rotate by root correction, scale by hip_scale
   * - Other tracks: skip
   */
  static retarget (
    source_clip: AnimationClip,
    corrections: Map<string, BindCorrection>,
    bone_mapping: Map<string, string>,
    options: BindCorrectionRetargetOptions
  ): AnimationClip {
    const reverse_mapping = RetargetUtils.reverse_bone_mapping_one_to_one(bone_mapping)
    const new_tracks: Array<QuaternionKeyframeTrack | VectorKeyframeTrack> = []
    const tmp_q = new Quaternion()
    const tmp_v = new Vector3()

    for (const track of source_clip.tracks) {
      const parts: TrackNameParts | null = RetargetUtils.parse_track_name_for_metadata(track.name)
      if (parts === null) continue

      const source_bone_name = parts.bone_name
      const property = parts.property

      const target_bone_name = reverse_mapping.get(source_bone_name)
      if (target_bone_name === undefined) continue

      if (property === 'quaternion') {
        const next = track.clone()
        next.name = `${target_bone_name}.quaternion`

        const correction = corrections.get(target_bone_name)
        if (correction !== undefined) {
          for (let i = 0; i < next.values.length; i += 4) {
            tmp_q.set(next.values[i], next.values[i + 1], next.values[i + 2], next.values[i + 3])
            if (correction.L !== null) tmp_q.premultiply(correction.L)
            if (correction.R !== null) tmp_q.multiply(correction.R)
            next.values[i] = tmp_q.x
            next.values[i + 1] = tmp_q.y
            next.values[i + 2] = tmp_q.z
            next.values[i + 3] = tmp_q.w
          }
        }

        new_tracks.push(next as QuaternionKeyframeTrack)
      } else if (property === 'position' && source_bone_name === (options.position_bone_name ?? '')) {
        const next = track.clone()
        next.name = `${target_bone_name}.position`

        for (let i = 0; i < next.values.length; i += 3) {
          if (options.root_correction_quat !== null) {
            tmp_v.set(next.values[i], next.values[i + 1], next.values[i + 2])
              .applyQuaternion(options.root_correction_quat)
            next.values[i] = tmp_v.x
            next.values[i + 1] = tmp_v.y
            next.values[i + 2] = tmp_v.z
          }
          if (options.hip_scale !== 1) {
            next.values[i] *= options.hip_scale
            next.values[i + 1] *= options.hip_scale
            next.values[i + 2] *= options.hip_scale
          }
        }

        new_tracks.push(next as VectorKeyframeTrack)
      }
    }

    const retargeted = source_clip.clone()
    retargeted.tracks = new_tracks
    return retargeted
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /tmp/mesh2motion-app && npx vitest run src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts`
Expected: PASS

- [ ] **Step 5: Write test — corrections are non-identity when rest poses differ**

Add to the same test file:

```typescript
    it('produces non-identity corrections when rest poses differ', () => {
      const source_root_q = new Quaternion().setFromAxisAngle(new Vector3(0, 1, 0), Math.PI / 4)
      const source_child_q = new Quaternion().setFromAxisAngle(new Vector3(1, 0, 0), Math.PI / 6)
      const target_root_q = new Quaternion().setFromAxisAngle(new Vector3(0, 1, 0), Math.PI / 3)
      const target_child_q = new Quaternion().setFromAxisAngle(new Vector3(1, 0, 0), -Math.PI / 4)

      const source = create_two_bone_skeleton(source_root_q, source_child_q)
      const target = create_two_bone_skeleton(target_root_q, target_child_q)
      const mapping = new Map([['Root', 'Root'], ['Child', 'Child']])

      const corrections = BindCorrectionRetargeter.compute_bind_corrections(source, target, mapping)

      expect(corrections.size).toBeGreaterThan(0)

      const child_corr = corrections.get('Child')
      expect(child_corr).toBeDefined()
      // at least one of L or R should be non-null
      expect(child_corr!.L !== null || child_corr!.R !== null).toBe(true)
    })
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd /tmp/mesh2motion-app && npx vitest run src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts`
Expected: PASS

- [ ] **Step 7: Write test — retarget produces identity output when corrections are empty**

```typescript
  describe('retarget', () => {
    it('renames tracks without modifying values when corrections are empty', () => {
      const times = new Float32Array([0, 1])
      const qvals = new Float32Array([0, 0, 0, 1, 0.707, 0, 0, 0.707])
      const source_clip = new AnimationClip('test', 1, [
        new QuaternionKeyframeTrack('pelvis.quaternion', times, qvals)
      ])
      const corrections = new Map<string, BindCorrection>()
      const mapping = new Map([['Hips', 'pelvis']])

      const result = BindCorrectionRetargeter.retarget(source_clip, corrections, mapping, {
        hip_scale: 1,
        root_correction_quat: null,
        position_bone_name: 'pelvis'
      })

      expect(result.tracks.length).toBe(1)
      expect(result.tracks[0].name).toBe('Hips.quaternion')

      // values should be unchanged
      for (let i = 0; i < qvals.length; i++) {
        expect((result.tracks[0] as QuaternionKeyframeTrack).values[i]).toBeCloseTo(qvals[i], 5)
      }
    })
  })
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd /tmp/mesh2motion-app && npx vitest run src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts`
Expected: PASS

- [ ] **Step 9: Write test — retarget applies L/R corrections to quaternion tracks**

```typescript
    it('applies L/R corrections to quaternion keyframes', () => {
      const times = new Float32Array([0])
      const identity_q = new Float32Array([0, 0, 0, 1])
      const source_clip = new AnimationClip('test', 0, [
        new QuaternionKeyframeTrack('pelvis.quaternion', times, identity_q)
      ])

      const L = new Quaternion().setFromAxisAngle(new Vector3(1, 0, 0), Math.PI / 2)
      const R = new Quaternion().setFromAxisAngle(new Vector3(0, 1, 0), Math.PI / 4)
      const corrections = new Map<string, BindCorrection>([
        ['Hips', { L, R }]
      ])
      const mapping = new Map([['Hips', 'pelvis']])

      const result = BindCorrectionRetargeter.retarget(source_clip, corrections, mapping, {
        hip_scale: 1,
        root_correction_quat: null,
        position_bone_name: 'pelvis'
      })

      // expected = L × identity × R = L × R
      const expected = L.clone().multiply(R)
      const actual = new Quaternion(
        result.tracks[0].values[0],
        result.tracks[0].values[1],
        result.tracks[0].values[2],
        result.tracks[0].values[3]
      )

      expect(actual.angleTo(expected)).toBeLessThan(1e-5)
    })
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd /tmp/mesh2motion-app && npx vitest run src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts`
Expected: PASS

- [ ] **Step 11: Write test — hip position track is scaled and rotated**

```typescript
    it('scales and rotates hip position track', () => {
      const times = new Float32Array([0])
      const pos_vals = new Float32Array([1, 2, 3])
      const source_clip = new AnimationClip('test', 0, [
        new VectorKeyframeTrack('pelvis.position', times, pos_vals)
      ])

      const corrections = new Map<string, BindCorrection>()
      const mapping = new Map([['Hips', 'pelvis']])

      const result = BindCorrectionRetargeter.retarget(source_clip, corrections, mapping, {
        hip_scale: 2,
        root_correction_quat: null,
        position_bone_name: 'pelvis'
      })

      expect(result.tracks.length).toBe(1)
      expect(result.tracks[0].name).toBe('Hips.position')
      expect(result.tracks[0].values[0]).toBeCloseTo(2, 5)
      expect(result.tracks[0].values[1]).toBeCloseTo(4, 5)
      expect(result.tracks[0].values[2]).toBeCloseTo(6, 5)
    })
```

- [ ] **Step 12: Run test to verify it passes**

Run: `cd /tmp/mesh2motion-app && npx vitest run src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts`
Expected: PASS

- [ ] **Step 13: Write round-trip test — identical skeletons produce unchanged output**

```typescript
    it('round-trips unchanged when source and target skeletons are identical', () => {
      const q = new Quaternion().setFromAxisAngle(new Vector3(0, 1, 0), Math.PI / 4)
      const cq = new Quaternion().setFromAxisAngle(new Vector3(1, 0, 0), Math.PI / 6)
      const source = create_two_bone_skeleton(q.clone(), cq.clone())
      const target = create_two_bone_skeleton(q.clone(), cq.clone())
      const mapping = new Map([['Root', 'Root'], ['Child', 'Child']])

      const corrections = BindCorrectionRetargeter.compute_bind_corrections(source, target, mapping)

      const times = new Float32Array([0])
      const anim_q = new Quaternion().setFromAxisAngle(new Vector3(0, 0, 1), 0.5)
      const qvals = new Float32Array([anim_q.x, anim_q.y, anim_q.z, anim_q.w])
      const source_clip = new AnimationClip('test', 0, [
        new QuaternionKeyframeTrack('Child.quaternion', times, qvals)
      ])

      const result = BindCorrectionRetargeter.retarget(source_clip, corrections, mapping, {
        hip_scale: 1,
        root_correction_quat: null,
        position_bone_name: null
      })

      expect(result.tracks.length).toBe(1)
      const actual = new Quaternion(
        result.tracks[0].values[0],
        result.tracks[0].values[1],
        result.tracks[0].values[2],
        result.tracks[0].values[3]
      )
      expect(actual.angleTo(anim_q)).toBeLessThan(1e-5)
    })
```

- [ ] **Step 14: Run all tests to verify they pass**

Run: `cd /tmp/mesh2motion-app && npx vitest run src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts`
Expected: all PASS

- [ ] **Step 15: Commit**

```bash
git -C /tmp/mesh2motion-app add src/retarget/human-retargeting/BindCorrectionRetargeter.ts src/retarget/human-retargeting/BindCorrectionRetargeter.test.ts
git -C /tmp/mesh2motion-app commit -m "feat: add BindCorrectionRetargeter with L/R rest-pose correction

Replaces swing-twist decomposition with per-bone bind correction matrices
that handle arbitrary rest-pose differences between source and target
skeletons. The formula:

  L = Rt × Wt⁻¹ × Ws × Rs⁻¹  (pre-multiply)
  R = Ws⁻¹ × Wt               (post-multiply)

correctly converts animation keyframes between any two humanoid skeletons
regardless of bone roll, fixing the arm-twist issue on Mixamo-rigged
targets (fixes #159).

Refs Mesh2Motion/mesh2motion-app#159"
```

---

## Batch 2: Wire into export path

### Task 2: Replace swing-twist path in AnimationRetargetService

**Files:**
- Modify: `src/retarget/AnimationRetargetService.ts` — replace `apply_human_swing_twist_retargeting` body

**Interfaces:**
- Consumes: `BindCorrectionRetargeter.compute_bind_corrections(Skeleton, Skeleton, Map) → Map`, `BindCorrectionRetargeter.retarget(AnimationClip, Map, Map, options) → AnimationClip`
- Produces: `retarget_animation_clip(AnimationClip) → AnimationClip` (unchanged public interface)

- [ ] **Step 1: Read the current `apply_human_swing_twist_retargeting` method**

Read `src/retarget/AnimationRetargetService.ts` lines 209-275 to confirm the method signature and understand the existing hip scale computation.

- [ ] **Step 2: Replace `apply_human_swing_twist_retargeting` with bind correction path**

Replace the method body. The key changes:
- Remove `Rig`, `HumanClipRetargeter` usage
- Use `BindCorrectionRetargeter.compute_bind_corrections` + `BindCorrectionRetargeter.retarget`
- Keep the existing bone mapping computation (`HumanChainConfig.build_mapped_chain_configs` is still used — its output feeds the bone mapping, even though chains are no longer used for retarget math)
- Keep the diagnostics logging

```typescript
  private apply_human_swing_twist_retargeting (source_clip: AnimationClip): AnimationClip {
    const source_skeleton: Skeleton | null = RetargetUtils.create_skeleton_from_group_object(this.source_armature)
    if (source_skeleton === null) {
      console.error('Failed to extract source skeleton from source armature.')
      return source_clip.clone()
    }

    if (this.target_skinned_meshes.length === 0) {
      console.error('No target skinned meshes available.')
      return source_clip.clone()
    }

    // clone the target skeleton so we don't mutate the live one
    const detached_target_skeleton: Skeleton = RetargetUtils.clone_skeleton(
      this.target_skinned_meshes[0].skeleton, this.target_rest_transforms)

    // compute bind corrections from rest-pose differences
    const corrections = BindCorrectionRetargeter.compute_bind_corrections(
      source_skeleton, detached_target_skeleton, this.get_bone_mappings())

    // hip height scaling
    let hip_scale = 1
    source_skeleton.bones[0]?.updateMatrixWorld(true)
    const source_hip_y = source_skeleton.bones[0]?.getWorldPosition(new Vector3()).y ?? 0
    const target_hip_y = detached_target_skeleton.bones[0]?.getWorldPosition(new Vector3()).y ?? 0
    if (source_hip_y > 0.05 && target_hip_y > 0.05) {
      hip_scale = Math.min(200, Math.max(0.2, target_hip_y / source_hip_y))
    }

    // root correction for rigs with tilted pelvis
    let root_correction_quat: Quaternion | null = null
    if (this.root_correction_x_degrees !== 0) {
      root_correction_quat = new Quaternion().setFromAxisAngle(
        new Vector3(1, 0, 0), this.root_correction_x_degrees * Math.PI / 180)
    }

    // find the position tracking bone name (M2M source name)
    const position_bone_name = source_skeleton.bones.find(
      b => b.name.toLowerCase().includes('pelvis') || b.name.toLowerCase().includes('hips')
    )?.name ?? null

    const retargeted_clip = BindCorrectionRetargeter.retarget(
      source_clip, corrections, this.get_bone_mappings(), {
        hip_scale,
        root_correction_quat,
        position_bone_name
      })

    retargeted_clip.name = `${source_clip.name} RT`
    console.log(`Bind-correction retargeting complete: ${source_clip.name} -> ${retargeted_clip.name}`)
    return retargeted_clip
  }
```

- [ ] **Step 3: Add the BindCorrectionRetargeter import**

Add at top of file:
```typescript
import { BindCorrectionRetargeter } from './human-retargeting/BindCorrectionRetargeter.ts'
```

Ensure `Vector3` is in the three.js import (add if missing).

- [ ] **Step 4: Run existing M2M tests to verify nothing is broken**

Run: `cd /tmp/mesh2motion-app && npx vitest run`
Expected: all existing tests PASS

- [ ] **Step 5: Commit**

```bash
git -C /tmp/mesh2motion-app add src/retarget/AnimationRetargetService.ts
git -C /tmp/mesh2motion-app commit -m "feat: wire BindCorrectionRetargeter into retarget export path

Replace the swing-twist bake loop in apply_human_swing_twist_retargeting
with the bind correction approach. The bone mapping infrastructure
(BoneAutoMapper, HumanChainConfig) is unchanged — only the rotation
math changes.

The swing-twist code (Retargeter, Rig, Pose) is preserved for the live
preview path but no longer used during export.

Refs Mesh2Motion/mesh2motion-app#159"
```

---

## Batch 3: Create PR

### Task 3: Branch, lint, and PR to mesh2motion-app

**Files:** none new — PR from the commits in Batch 1-2

- [ ] **Step 1: Create a feature branch**

```bash
git -C /tmp/mesh2motion-app checkout -b fix/bind-correction-retarget
git -C /tmp/mesh2motion-app cherry-pick <batch-1-sha> <batch-2-sha>
```

(Or if already on a branch, just verify the commits are there.)

- [ ] **Step 2: Run linter**

Run: `cd /tmp/mesh2motion-app && npx eslint src/retarget/human-retargeting/BindCorrectionRetargeter.ts src/retarget/AnimationRetargetService.ts`
Fix any lint issues.

- [ ] **Step 3: Run full test suite**

Run: `cd /tmp/mesh2motion-app && npx vitest run`
Expected: all PASS

- [ ] **Step 4: Push and create PR**

```bash
git -C /tmp/mesh2motion-app push -u origin fix/bind-correction-retarget
```

PR title: `fix: replace swing-twist retarget with bind correction for cross-rig export`

PR body: Reference #159, explain the L/R formula, note that live preview still uses swing-twist, link to the test results.

---

## References

- `2026-11-12-m2m-retarget-bind-correction-design.md` — design spec
- `src/retarget/AnimationRetargetService.ts` — current retarget entry point
- `src/retarget/human-retargeting/Retargeter.ts:48-80` — current swing-twist bake loop
- `src/retarget/human-retargeting/HumanChainConfig.ts:7-25` — chain definitions
- `src/retarget/RetargetUtils.ts:103-159` — rest pose capture utilities
- casehub-agency `retarget.mjs:1034-1057` — proven bind correction implementation
- Mesh2Motion/mesh2motion-app#159 — upstream bug report
- casehubio/agency#455 — CC0 animation library issue

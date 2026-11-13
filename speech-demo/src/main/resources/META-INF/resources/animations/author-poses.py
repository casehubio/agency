#!/usr/bin/env python3
"""Author 23 conversational body poses on the Mixamo/M2M skeleton.

Run:
    blender --background --python author-poses.py -- <source-glb> <output-dir>

Imports an existing CC0 GLB to get the armature with correct rest pose,
then creates each pose as a separate Action and exports individual GLBs.
"""
import bpy
import sys
import os
from math import radians, sin, cos, pi
from mathutils import Quaternion, Vector

argv = sys.argv[sys.argv.index("--") + 1:]
SOURCE_GLB = argv[0]
OUTPUT_DIR = argv[1]
os.makedirs(OUTPUT_DIR, exist_ok=True)

FPS = 20
DURATION = 3.0  # seconds
TOTAL_FRAMES = int(FPS * DURATION)  # 60 frames
B = "mixamorig:"  # bone prefix

# ── Reference quaternions from existing CC0 animations ────────────────
# Format: (w, x, y, z) — Blender convention

# Neutral standing from idle-breathe frame 0
NEUTRAL = {
    "Hips":           (0.998, 0.053, 0.001, 0.035),
    "Spine":          (0.999, 0.048, 0.006, -0.018),
    "Spine1":         (1.0, 0.012, 0.003, -0.002),
    "Spine2":         (1.0, 0.013, 0.004, -0.003),
    "LeftShoulder":   (0.994, 0.009, 0.001, -0.106),
    "LeftArm":        (0.844, 0.109, -0.167, -0.497),
    "LeftForeArm":    (0.987, 0.003, -0.162, 0.002),
    "LeftHand":       (1.0, 0.0, 0.0, 0.0),
    "RightShoulder":  (0.996, 0.006, -0.005, 0.087),
    "RightArm":       (0.872, -0.094, 0.163, 0.453),
    "RightForeArm":   (0.637, -0.002, 0.771, 0.003),
    "RightHand":      (1.0, 0.0, 0.0, 0.0),
    "Neck":           (1.0, 0.010, -0.006, 0.002),
    "Head":           (1.0, 0.0, 0.0, 0.0),
    "RightUpLeg":     (0.985, -0.141, -0.082, -0.057),
    "RightLeg":       (0.978, 0.191, -0.080, -0.029),
    "RightFoot":      (0.993, -0.101, -0.034, 0.047),
    "RightToeBase":   (1.0, 0.0, 0.0, 0.0),
    "LeftUpLeg":      (0.994, -0.101, 0.035, -0.021),
    "LeftLeg":        (0.991, 0.133, 0.012, 0.003),
    "LeftFoot":       (0.995, -0.095, -0.010, -0.013),
    "LeftToeBase":    (1.0, 0.0, 0.0, 0.0),
}
NEUTRAL_HIPS_LOC = (0.0, -0.021, 0.0)

# Arm raised (from greeting-wave)
L_ARM_RAISED = (0.628, -0.339, -0.678, -0.176)
L_FOREARM_WAVEUP = (0.818, 0.003, -0.575, -0.006)

# Arm forward pointing (from pointing f30)
R_ARM_FORWARD = (0.819, 0.038, 0.291, 0.493)
R_FOREARM_EXTENDED = (0.821, 0.001, 0.571, -0.002)

# Arms crossed (from sad)
L_ARM_CROSSED = (0.638, -0.308, -0.504, -0.495)
L_FOREARM_TIGHT = (0.447, -0.008, -0.895, -0.001)
R_ARM_CROSSED = (0.633, -0.406, 0.480, 0.452)
R_FOREARM_TIGHT = (0.496, 0.003, 0.869, -0.001)

# Sad body from sad animation
SAD_HIPS = (0.999, -0.035, 0.0, 0.017)
SAD_SPINE = (0.998, 0.061, -0.001, -0.004)
SAD_NECK = (0.999, 0.052, -0.014, 0.007)
SAD_HIPS_LOC = (0.0, -0.302, 0.001)


# ── Helpers ───────────────────────────────────────────────────────────

def Q(t):
    return Quaternion(t)

def slerp(a, b, t):
    return Q(a).slerp(Q(b), t)

def delta(axis, deg):
    """Small rotation around a local axis."""
    return Quaternion(Vector(axis), radians(deg))

def apply(base, axis, deg):
    """Apply delta rotation in the base's local frame."""
    return Q(base) @ delta(axis, deg)

def identity():
    return (1.0, 0.0, 0.0, 0.0)

def breath_offset(frame, amplitude=1.0):
    """Sinusoidal breathing phase (0-1-0 over the animation)."""
    t = frame / (TOTAL_FRAMES - 1)
    return sin(t * 2 * pi) * amplitude

def ease_in_out(t):
    """Smoothstep ease-in-out."""
    return t * t * (3 - 2 * t)

def motion_curve(frame, peak_start=10, peak_end=45):
    """0→1 ease-in to peak, hold, 1→0 ease-out."""
    if frame <= peak_start:
        return ease_in_out(frame / peak_start)
    elif frame >= peak_end:
        remaining = TOTAL_FRAMES - 1 - peak_end
        if remaining <= 0:
            return 0.0
        return 1.0 - ease_in_out((frame - peak_end) / remaining)
    else:
        return 1.0


# ── Pose definitions ─────────────────────────────────────────────────
# Each pose: name, type (loop/oneshot), base bone quaternions,
# optional hips_loc, and frame-varying modifications.

def make_hands_clasped_front():
    """Formal/attentive, hands together at waist."""
    base = dict(NEUTRAL)
    # Bring both arms forward and inward, forearms bent to clasp hands
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), 3))
    base["Spine1"] = tuple(apply(NEUTRAL["Spine1"], (1, 0, 0), 2))
    # Left arm: bring forward and inward from side
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_CROSSED, 0.45))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_TIGHT, 0.55))
    # Right arm: mirror
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_CROSSED, 0.45))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_TIGHT, 0.55))
    return base

def make_hands_behind_back():
    """Casual/thoughtful, weight shifted to one side."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), -2))
    # Weight shift: tilt hips slightly
    base["Hips"] = tuple(apply(NEUTRAL["Hips"], (0, 0, 1), 3))
    # Left arm: bring backward — rotate arm backward from hanging position
    base["LeftArm"] = tuple(apply(NEUTRAL["LeftArm"], (1, 0, 0), -25))
    base["LeftForeArm"] = tuple(apply(NEUTRAL["LeftForeArm"], (0, 1, 0), -40))
    # Right arm: same
    base["RightArm"] = tuple(apply(NEUTRAL["RightArm"], (1, 0, 0), -25))
    base["RightForeArm"] = tuple(apply(NEUTRAL["RightForeArm"], (0, 1, 0), 40))
    # Slight leg weight shift
    base["RightUpLeg"] = tuple(apply(NEUTRAL["RightUpLeg"], (0, 0, 1), -3))
    base["LeftUpLeg"] = tuple(apply(NEUTRAL["LeftUpLeg"], (0, 0, 1), 3))
    return base

def make_thinker():
    """Fist under chin, one arm across waist, head tilted forward."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), 4))
    base["Neck"] = tuple(apply(NEUTRAL["Neck"], (1, 0, 0), 8))
    base["Head"] = tuple(apply(identity(), (0, 0, 1), -5))
    # Right arm across waist (supporting elbow)
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_CROSSED, 0.5))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_TIGHT, 0.45))
    # Left arm: elbow on right hand, forearm up to chin
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_CROSSED, 0.55))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_WAVEUP, 0.7))
    # Weight shift
    base["Hips"] = tuple(apply(NEUTRAL["Hips"], (0, 0, 1), 2))
    return base

def make_hands_on_hips():
    """Assertive/defensive, elbows out, hands at hip level."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), -3))
    base["Spine2"] = tuple(apply(NEUTRAL["Spine2"], (1, 0, 0), -2))
    # Left arm: out slightly, forearm bent to hip — arm stays near side but rotated out
    base["LeftArm"] = tuple(apply(NEUTRAL["LeftArm"], (1, 0, 0), 15))
    base["LeftArm"] = tuple(apply(base["LeftArm"], (0, 1, 0), -15))
    base["LeftForeArm"] = tuple(apply(NEUTRAL["LeftForeArm"], (0, 1, 0), -60))
    # Right arm: mirror
    base["RightArm"] = tuple(apply(NEUTRAL["RightArm"], (1, 0, 0), 15))
    base["RightArm"] = tuple(apply(base["RightArm"], (0, 1, 0), 15))
    base["RightForeArm"] = tuple(apply(NEUTRAL["RightForeArm"], (0, 1, 0), 60))
    return base

def make_open_palms_presenting():
    """Both hands forward, palms up — 'here is what I think'."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), 2))
    # Both arms partially forward
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_CROSSED, 0.35))
    base["LeftArm"] = tuple(apply(base["LeftArm"], (1, 0, 0), 10))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_TIGHT, 0.35))
    base["LeftHand"] = tuple(delta((0, 1, 0), -20))
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.4))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_EXTENDED, 0.35))
    base["RightHand"] = tuple(delta((0, 1, 0), 20))
    return base

# ── Hand gesture cycle poses ─────────────────────────────────────────

def _hands_base():
    """Base position for hand gestures: hands at stomach, palms inward."""
    base = dict(NEUTRAL)
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_CROSSED, 0.4))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_TIGHT, 0.45))
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_CROSSED, 0.4))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_TIGHT, 0.45))
    return base

def _hands_chest():
    """Hands raised to chest level — escalating engagement."""
    base = _hands_base()
    base["LeftArm"] = tuple(apply(base["LeftArm"], (1, 0, 0), 12))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 12))
    base["LeftForeArm"] = tuple(apply(base["LeftForeArm"], (0, 1, 0), -15))
    base["RightForeArm"] = tuple(apply(base["RightForeArm"], (0, 1, 0), 15))
    return base

def _hands_expand():
    """Hands widen, palms tilt up — opening up an idea."""
    base = _hands_base()
    base["LeftArm"] = tuple(apply(base["LeftArm"], (0, 0, 1), -12))
    base["LeftArm"] = tuple(apply(base["LeftArm"], (1, 0, 0), 8))
    base["RightArm"] = tuple(apply(base["RightArm"], (0, 0, 1), 12))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 8))
    base["LeftHand"] = tuple(delta((1, 0, 0), -10))
    base["RightHand"] = tuple(delta((1, 0, 0), 10))
    return base

def _hands_converge():
    """Hands come together, fingertips touching — narrowing to conclusion."""
    base = _hands_base()
    base["LeftArm"] = tuple(apply(base["LeftArm"], (0, 0, 1), 8))
    base["RightArm"] = tuple(apply(base["RightArm"], (0, 0, 1), -8))
    base["LeftForeArm"] = tuple(apply(base["LeftForeArm"], (0, 1, 0), -12))
    base["RightForeArm"] = tuple(apply(base["RightForeArm"], (0, 1, 0), 12))
    return base

# ── Reactive gesture poses ───────────────────────────────────────────

def make_head_shake():
    """Side-to-side head rotation, body follows slightly."""
    base = dict(NEUTRAL)
    base["Head"] = tuple(delta((0, 0, 1), 18))
    base["Neck"] = tuple(apply(NEUTRAL["Neck"], (0, 0, 1), 5))
    base["Spine2"] = tuple(apply(NEUTRAL["Spine2"], (0, 0, 1), 2))
    return base

def make_head_shake_other():
    """Opposite direction of head shake."""
    base = dict(NEUTRAL)
    base["Head"] = tuple(delta((0, 0, 1), -18))
    base["Neck"] = tuple(apply(NEUTRAL["Neck"], (0, 0, 1), -5))
    base["Spine2"] = tuple(apply(NEUTRAL["Spine2"], (0, 0, 1), -2))
    return base

def make_finger_wag():
    """Arm out, index finger up, swaying side to side."""
    base = dict(NEUTRAL)
    # Right arm forward and up
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.65))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_EXTENDED, 0.5))
    base["RightHand"] = tuple(delta((1, 0, 0), 15))
    base["Spine2"] = tuple(apply(NEUTRAL["Spine2"], (0, 0, 1), -3))
    return base

def make_finger_wag_sway():
    """Finger wag sway to other side."""
    base = make_finger_wag()
    base["RightHand"] = tuple(delta((0, 0, 1), 20))
    base["Spine2"] = tuple(apply(NEUTRAL["Spine2"], (0, 0, 1), 3))
    return base

def make_shrug():
    """Palms up, shoulders raised."""
    base = dict(NEUTRAL)
    # Raise shoulders
    base["LeftShoulder"] = tuple(apply(NEUTRAL["LeftShoulder"], (1, 0, 0), 12))
    base["RightShoulder"] = tuple(apply(NEUTRAL["RightShoulder"], (1, 0, 0), 12))
    # Arms slightly out with palms up
    base["LeftArm"] = tuple(apply(NEUTRAL["LeftArm"], (0, 0, 1), 15))
    base["LeftArm"] = tuple(apply(base["LeftArm"], (1, 0, 0), 10))
    base["RightArm"] = tuple(apply(NEUTRAL["RightArm"], (0, 0, 1), -15))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 10))
    # Forearms out and up
    base["LeftForeArm"] = tuple(apply(NEUTRAL["LeftForeArm"], (0, 1, 0), -35))
    base["RightForeArm"] = tuple(apply(NEUTRAL["RightForeArm"], (0, 1, 0), 35))
    # Slight head tilt
    base["Head"] = tuple(delta((0, 0, 1), 5))
    base["Neck"] = tuple(apply(NEUTRAL["Neck"], (0, 0, 1), 3))
    return base

def make_one_hand_raised():
    """Right palm out — 'wait' / 'hold on'."""
    base = dict(NEUTRAL)
    # Right arm raised forward
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.7))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_EXTENDED, 0.55))
    base["RightHand"] = tuple(delta((1, 0, 0), -10))
    base["Spine2"] = tuple(apply(NEUTRAL["Spine2"], (0, 0, 1), -2))
    return base

def make_thumbs_up():
    """Right arm forward, thumbs up approval."""
    base = dict(NEUTRAL)
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.55))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_EXTENDED, 0.4))
    base["RightHand"] = tuple(delta((0, 0, 1), -15))
    base["Spine2"] = tuple(apply(NEUTRAL["Spine2"], (1, 0, 0), -2))
    return base

def make_finger_pointing_up():
    """Right finger raised — 'aha!' / 'one thing'."""
    base = dict(NEUTRAL)
    # Right arm raised, pointing up
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.5))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 15))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_EXTENDED, 0.35))
    base["RightForeArm"] = tuple(apply(base["RightForeArm"], (1, 0, 0), 15))
    base["RightHand"] = tuple(delta((1, 0, 0), 20))
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), -2))
    return base

# ── Emotional body poses ─────────────────────────────────────────────

def make_self_hug():
    """Arms hugging self, hunched over."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), 10))
    base["Spine1"] = tuple(apply(NEUTRAL["Spine1"], (1, 0, 0), 6))
    base["Neck"] = tuple(apply(NEUTRAL["Neck"], (1, 0, 0), 8))
    # Arms crossed tightly
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_CROSSED, 0.75))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_TIGHT, 0.8))
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_CROSSED, 0.75))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_TIGHT, 0.8))
    # Shoulders pulled in
    base["LeftShoulder"] = tuple(apply(NEUTRAL["LeftShoulder"], (1, 0, 0), 6))
    base["RightShoulder"] = tuple(apply(NEUTRAL["RightShoulder"], (1, 0, 0), 6))
    base["Hips"] = SAD_HIPS
    return base, SAD_HIPS_LOC

def make_arms_wide_open():
    """Expansive open arms — bigger than victory, joyful."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), -5))
    base["Spine1"] = tuple(apply(NEUTRAL["Spine1"], (1, 0, 0), -3))
    base["Spine2"] = tuple(apply(NEUTRAL["Spine2"], (1, 0, 0), -3))
    # Arms wide open — raised and spread
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_RAISED, 0.55))
    base["LeftArm"] = tuple(apply(base["LeftArm"], (0, 0, 1), 10))
    base["LeftForeArm"] = tuple(apply(NEUTRAL["LeftForeArm"], (0, 1, 0), -15))
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.6))
    base["RightArm"] = tuple(apply(base["RightArm"], (0, 0, 1), -10))
    base["RightForeArm"] = tuple(apply(NEUTRAL["RightForeArm"], (0, 1, 0), 15))
    # Head up
    base["Head"] = tuple(delta((1, 0, 0), -5))
    return base

def make_facepalm():
    """One hand on forehead, other arm limp."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), 5))
    base["Neck"] = tuple(apply(NEUTRAL["Neck"], (1, 0, 0), 8))
    # Right hand to forehead
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.7))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 20))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_EXTENDED, 0.65))
    base["RightForeArm"] = tuple(apply(base["RightForeArm"], (1, 0, 0), 20))
    # Left arm hangs limply
    base["LeftArm"] = tuple(apply(NEUTRAL["LeftArm"], (1, 0, 0), -5))
    base["Head"] = tuple(delta((1, 0, 0), 5))
    return base

def make_shocked_recoil():
    """Hands up near face, stepped back — 'what?!'"""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), -8))
    # Both hands up near face
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_RAISED, 0.45))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_WAVEUP, 0.55))
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.55))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 15))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_EXTENDED, 0.5))
    base["RightForeArm"] = tuple(apply(base["RightForeArm"], (1, 0, 0), 15))
    # Lean back
    base["Head"] = tuple(delta((1, 0, 0), -8))
    base["Hips"] = tuple(apply(NEUTRAL["Hips"], (1, 0, 0), -5))
    return base, (0.0, -0.01, 0.02)  # step back

def make_hands_over_mouth():
    """Both hands clasped over mouth — 'oh no'."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), 3))
    base["Neck"] = tuple(apply(NEUTRAL["Neck"], (1, 0, 0), 3))
    # Both hands to mouth
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_CROSSED, 0.6))
    base["LeftArm"] = tuple(apply(base["LeftArm"], (1, 0, 0), 20))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_WAVEUP, 0.6))
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_CROSSED, 0.6))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 20))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_TIGHT, 0.55))
    base["RightForeArm"] = tuple(apply(base["RightForeArm"], (1, 0, 0), 15))
    return base

def make_hand_over_mouth():
    """Single hand covering mouth — lighter surprise / 'oops'."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), 2))
    # Right hand to mouth
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_CROSSED, 0.5))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 18))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_TIGHT, 0.5))
    base["RightForeArm"] = tuple(apply(base["RightForeArm"], (1, 0, 0), 15))
    # Head slight tilt
    base["Head"] = tuple(delta((0, 0, 1), 5))
    return base

def make_hands_on_head():
    """Both hands on head — overwhelmed/exasperation."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), -3))
    # Both arms up to head
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_RAISED, 0.7))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_WAVEUP, 0.75))
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_FORWARD, 0.7))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 20))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_EXTENDED, 0.65))
    base["RightForeArm"] = tuple(apply(base["RightForeArm"], (1, 0, 0), 15))
    base["Head"] = tuple(delta((1, 0, 0), -5))
    return base

def make_hands_interlocked_chest():
    """Fingers interlocked at chest — nervous/worried."""
    base = dict(NEUTRAL)
    base["Spine"] = tuple(apply(NEUTRAL["Spine"], (1, 0, 0), 4))
    base["Spine1"] = tuple(apply(NEUTRAL["Spine1"], (1, 0, 0), 2))
    # Both arms in front of chest, hands together
    base["LeftArm"] = tuple(slerp(NEUTRAL["LeftArm"], L_ARM_CROSSED, 0.5))
    base["LeftArm"] = tuple(apply(base["LeftArm"], (1, 0, 0), 10))
    base["LeftForeArm"] = tuple(slerp(NEUTRAL["LeftForeArm"], L_FOREARM_TIGHT, 0.6))
    base["RightArm"] = tuple(slerp(NEUTRAL["RightArm"], R_ARM_CROSSED, 0.5))
    base["RightArm"] = tuple(apply(base["RightArm"], (1, 0, 0), 10))
    base["RightForeArm"] = tuple(slerp(NEUTRAL["RightForeArm"], R_FOREARM_TIGHT, 0.55))
    # Slight shoulder raise
    base["LeftShoulder"] = tuple(apply(NEUTRAL["LeftShoulder"], (1, 0, 0), 4))
    base["RightShoulder"] = tuple(apply(NEUTRAL["RightShoulder"], (1, 0, 0), 4))
    return base


# ── All 23 pose definitions ──────────────────────────────────────────

POSES = []

# P1 — Conversational stances (looping breathing)
POSES.append({
    "name": "hands-clasped-front",
    "type": "loop",
    "make": lambda: (make_hands_clasped_front(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "hands-behind-back",
    "type": "loop",
    "make": lambda: (make_hands_behind_back(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "thinker",
    "type": "loop",
    "make": lambda: (make_thinker(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "hands-on-hips",
    "type": "loop",
    "make": lambda: (make_hands_on_hips(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "open-palms-presenting",
    "type": "loop",
    "make": lambda: (make_open_palms_presenting(), NEUTRAL_HIPS_LOC),
})

# P1 — Hand gesture cycle (looping, start=end for seamless crossfade)
POSES.append({
    "name": "hands-base-to-chest",
    "type": "gesture-cycle",
    "make_start": _hands_base,
    "make_peak": _hands_chest,
})
POSES.append({
    "name": "hands-expand",
    "type": "gesture-cycle",
    "make_start": _hands_base,
    "make_peak": _hands_expand,
})
POSES.append({
    "name": "hands-converge",
    "type": "gesture-cycle",
    "make_start": _hands_base,
    "make_peak": _hands_converge,
})
POSES.append({
    "name": "hands-alternating",
    "type": "gesture-alt",
    # Special: alternating left/right hand forward
})

# P2 — Reactive gestures (one-shot)
POSES.append({
    "name": "head-shake",
    "type": "oscillate",
    "make_left": make_head_shake,
    "make_right": make_head_shake_other,
})
POSES.append({
    "name": "finger-wag",
    "type": "oscillate",
    "make_left": make_finger_wag,
    "make_right": make_finger_wag_sway,
})
POSES.append({
    "name": "shrug",
    "type": "oneshot",
    "make": lambda: (make_shrug(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "one-hand-raised",
    "type": "oneshot",
    "make": lambda: (make_one_hand_raised(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "thumbs-up",
    "type": "oneshot",
    "make": lambda: (make_thumbs_up(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "finger-pointing-up",
    "type": "oneshot",
    "make": lambda: (make_finger_pointing_up(), NEUTRAL_HIPS_LOC),
})

# P2 — Emotional body poses (one-shot or held)
POSES.append({
    "name": "self-hug",
    "type": "oneshot",
    "make": make_self_hug,
})
POSES.append({
    "name": "arms-wide-open",
    "type": "oneshot",
    "make": lambda: (make_arms_wide_open(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "facepalm",
    "type": "oneshot",
    "make": lambda: (make_facepalm(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "shocked-recoil",
    "type": "oneshot",
    "make": make_shocked_recoil,
})
POSES.append({
    "name": "hands-over-mouth",
    "type": "oneshot",
    "make": lambda: (make_hands_over_mouth(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "hand-over-mouth",
    "type": "oneshot",
    "make": lambda: (make_hand_over_mouth(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "hands-on-head",
    "type": "oneshot",
    "make": lambda: (make_hands_on_head(), NEUTRAL_HIPS_LOC),
})
POSES.append({
    "name": "hands-interlocked-chest",
    "type": "oneshot",
    "make": lambda: (make_hands_interlocked_chest(), NEUTRAL_HIPS_LOC),
})


# ── Animation creation ───────────────────────────────────────────────

def set_pose(armature, bones_dict, hips_loc=None):
    """Set all pose bones from a quaternion dictionary."""
    for bone_name, quat in bones_dict.items():
        pb = armature.pose.bones.get(B + bone_name)
        if pb:
            pb.rotation_quaternion = Q(quat)
    if hips_loc:
        hips = armature.pose.bones.get(B + "Hips")
        if hips:
            hips.location = Vector(hips_loc)

def keyframe_all(armature, frame, bones_dict, hips_loc=None):
    """Insert keyframes for all bones at the given frame."""
    for bone_name in bones_dict:
        pb = armature.pose.bones.get(B + bone_name)
        if pb:
            pb.keyframe_insert(data_path="rotation_quaternion", frame=frame)
    if hips_loc:
        hips = armature.pose.bones.get(B + "Hips")
        if hips:
            hips.keyframe_insert(data_path="location", frame=frame)

def add_breathing(armature, base_bones, hips_loc, frame):
    """Apply visible breathing and idle motion overlay to a base pose."""
    t = frame / (TOTAL_FRAMES - 1)
    breath = sin(t * 2 * pi)           # primary breathing cycle
    drift = sin(t * pi)                 # slower weight-shift arc (half cycle)
    sway = sin(t * 2.5 * pi) * 0.6     # asymmetric micro-sway

    modified = dict(base_bones)

    # Chest expansion on inhale — visible rise and fall
    modified["Spine"] = tuple(apply(base_bones.get("Spine", NEUTRAL["Spine"]),
                                    (1, 0, 0), breath * 2.0))
    modified["Spine1"] = tuple(apply(base_bones.get("Spine1", NEUTRAL["Spine1"]),
                                     (1, 0, 0), breath * 3.5))
    modified["Spine2"] = tuple(apply(base_bones.get("Spine2", NEUTRAL["Spine2"]),
                                     (1, 0, 0), breath * 2.5))

    # Shoulder rise on inhale
    modified["LeftShoulder"] = tuple(apply(
        base_bones.get("LeftShoulder", NEUTRAL["LeftShoulder"]),
        (1, 0, 0), breath * 2.5))
    modified["RightShoulder"] = tuple(apply(
        base_bones.get("RightShoulder", NEUTRAL["RightShoulder"]),
        (1, 0, 0), breath * 2.0))

    # Subtle lateral weight shift — hips and spine tilt slightly
    modified["Hips"] = tuple(apply(
        base_bones.get("Hips", NEUTRAL["Hips"]),
        (0, 0, 1), drift * 1.5))
    modified["Spine"] = tuple(apply(modified["Spine"],
                                    (0, 0, 1), drift * -0.8))

    # Arm drift — arms sway slightly with the body
    modified["LeftArm"] = tuple(apply(
        base_bones.get("LeftArm", NEUTRAL["LeftArm"]),
        (1, 0, 0), sway * 1.5))
    modified["RightArm"] = tuple(apply(
        base_bones.get("RightArm", NEUTRAL["RightArm"]),
        (1, 0, 0), sway * -1.2))

    # Head micro-movement — slight tilt follows breathing
    modified["Head"] = tuple(apply(
        base_bones.get("Head", NEUTRAL["Head"]),
        (1, 0, 0), breath * 1.0))
    modified["Head"] = tuple(apply(modified["Head"],
                                   (0, 0, 1), drift * 1.0))

    # Hips vertical shift (breathing lifts the chest)
    mod_loc = list(hips_loc) if hips_loc else list(NEUTRAL_HIPS_LOC)
    mod_loc[1] += breath * 0.006
    # Slight lateral sway
    mod_loc[0] += drift * 0.003
    return modified, tuple(mod_loc)


def create_loop_animation(armature, pose_def):
    """Create a looping breathing animation for a stance pose."""
    result = pose_def["make"]()
    if isinstance(result, tuple) and len(result) == 2:
        base_bones, hips_loc = result
    else:
        base_bones = result
        hips_loc = NEUTRAL_HIPS_LOC

    name = pose_def["name"]
    action = bpy.data.actions.new(name=name)
    if not armature.animation_data:
        armature.animation_data_create()
    armature.animation_data.action = action

    # Keyframe at every 5th frame for smooth breathing
    for frame in range(0, TOTAL_FRAMES, 5):
        bpy.context.scene.frame_set(frame)
        modified, mod_loc = add_breathing(armature, base_bones, hips_loc, frame)
        set_pose(armature, modified, mod_loc)
        keyframe_all(armature, frame, modified, mod_loc)

    # Ensure last frame matches first for seamless loop
    bpy.context.scene.frame_set(TOTAL_FRAMES - 1)
    modified_0, mod_loc_0 = add_breathing(armature, base_bones, hips_loc, 0)
    set_pose(armature, modified_0, mod_loc_0)
    keyframe_all(armature, TOTAL_FRAMES - 1, modified_0, mod_loc_0)

    return action


def create_gesture_cycle(armature, pose_def):
    """Create a looping gesture cycle: start → peak → start."""
    start_bones = pose_def["make_start"]()
    peak_bones = pose_def["make_peak"]()
    name = pose_def["name"]
    action = bpy.data.actions.new(name=name)
    if not armature.animation_data:
        armature.animation_data_create()
    armature.animation_data.action = action

    hips_loc = NEUTRAL_HIPS_LOC

    for frame in range(0, TOTAL_FRAMES):
        bpy.context.scene.frame_set(frame)
        t = frame / (TOTAL_FRAMES - 1)
        # Smooth bell curve: 0→1→0
        blend = sin(t * pi)
        # Slerp between start and peak
        blended = {}
        all_keys = set(start_bones.keys()) | set(peak_bones.keys())
        for bone_name in all_keys:
            s = start_bones.get(bone_name, NEUTRAL.get(bone_name, identity()))
            p = peak_bones.get(bone_name, s)
            blended[bone_name] = tuple(slerp(s, p, blend))

        # Add subtle breathing
        blended, mod_loc = add_breathing(armature, blended, hips_loc, frame)
        set_pose(armature, blended, mod_loc)
        # Keyframe every 3rd frame for smooth motion
        if frame % 3 == 0 or frame == TOTAL_FRAMES - 1:
            keyframe_all(armature, frame, blended, mod_loc)

    return action


def create_alternating_gesture(armature, pose_def):
    """Create hands-alternating: left forward, then right, cycling."""
    base = _hands_base()
    name = pose_def["name"]
    action = bpy.data.actions.new(name=name)
    if not armature.animation_data:
        armature.animation_data_create()
    armature.animation_data.action = action

    hips_loc = NEUTRAL_HIPS_LOC

    for frame in range(0, TOTAL_FRAMES):
        bpy.context.scene.frame_set(frame)
        t = frame / (TOTAL_FRAMES - 1)
        # Left hand forward for first half, right for second
        left_blend = max(0, sin(t * 2 * pi))
        right_blend = max(0, sin(t * 2 * pi + pi))

        blended = dict(base)
        # Left hand pushes forward
        blended["LeftArm"] = tuple(slerp(base["LeftArm"],
            tuple(apply(base["LeftArm"], (1, 0, 0), 12)), left_blend))
        blended["LeftForeArm"] = tuple(slerp(base["LeftForeArm"],
            tuple(apply(base["LeftForeArm"], (0, 1, 0), -10)), left_blend))
        # Right hand pushes forward
        blended["RightArm"] = tuple(slerp(base["RightArm"],
            tuple(apply(base["RightArm"], (1, 0, 0), 12)), right_blend))
        blended["RightForeArm"] = tuple(slerp(base["RightForeArm"],
            tuple(apply(base["RightForeArm"], (0, 1, 0), 10)), right_blend))

        blended, mod_loc = add_breathing(armature, blended, hips_loc, frame)
        set_pose(armature, blended, mod_loc)
        if frame % 3 == 0 or frame == TOTAL_FRAMES - 1:
            keyframe_all(armature, frame, blended, mod_loc)

    return action


def create_oscillate(armature, pose_def):
    """Create an oscillating motion (head shake, finger wag)."""
    left_bones = pose_def["make_left"]()
    right_bones = pose_def["make_right"]()
    name = pose_def["name"]
    action = bpy.data.actions.new(name=name)
    if not armature.animation_data:
        armature.animation_data_create()
    armature.animation_data.action = action

    hips_loc = NEUTRAL_HIPS_LOC

    for frame in range(0, TOTAL_FRAMES):
        bpy.context.scene.frame_set(frame)
        t = frame / (TOTAL_FRAMES - 1)

        # Build-up → oscillate → settle
        if t < 0.1:
            # Ease into first position
            envelope = ease_in_out(t / 0.1)
            blend = envelope  # 0→1 going to left
            blended = {}
            all_keys = set(NEUTRAL.keys()) | set(left_bones.keys())
            for bone_name in all_keys:
                n = NEUTRAL.get(bone_name, identity())
                l = left_bones.get(bone_name, n)
                blended[bone_name] = tuple(slerp(n, l, blend))
        elif t > 0.85:
            # Settle back to neutral
            envelope = 1.0 - ease_in_out((t - 0.85) / 0.15)
            # Use left as the final oscillation
            blended = {}
            all_keys = set(NEUTRAL.keys()) | set(left_bones.keys())
            for bone_name in all_keys:
                n = NEUTRAL.get(bone_name, identity())
                l = left_bones.get(bone_name, n)
                blended[bone_name] = tuple(slerp(n, l, envelope * 0.3))
        else:
            # Oscillate: 3 full cycles between 0.1 and 0.85
            osc_t = (t - 0.1) / 0.75
            osc = sin(osc_t * 3 * 2 * pi)
            blended = {}
            all_keys = set(NEUTRAL.keys()) | set(left_bones.keys()) | set(right_bones.keys())
            for bone_name in all_keys:
                l = left_bones.get(bone_name, NEUTRAL.get(bone_name, identity()))
                r = right_bones.get(bone_name, NEUTRAL.get(bone_name, identity()))
                mid = tuple(slerp(l, r, 0.5))
                if osc >= 0:
                    blended[bone_name] = tuple(slerp(mid, l, osc))
                else:
                    blended[bone_name] = tuple(slerp(mid, r, -osc))

        set_pose(armature, blended, hips_loc)
        if frame % 2 == 0 or frame == TOTAL_FRAMES - 1:
            keyframe_all(armature, frame, blended, hips_loc)

    return action


def create_oneshot(armature, pose_def):
    """Create a one-shot gesture: neutral → peak → hold → settle."""
    result = pose_def["make"]()
    if isinstance(result, tuple) and len(result) == 2:
        peak_bones, peak_loc = result
    else:
        peak_bones = result
        peak_loc = NEUTRAL_HIPS_LOC

    name = pose_def["name"]
    action = bpy.data.actions.new(name=name)
    if not armature.animation_data:
        armature.animation_data_create()
    armature.animation_data.action = action

    for frame in range(0, TOTAL_FRAMES):
        bpy.context.scene.frame_set(frame)
        t = motion_curve(frame, peak_start=12, peak_end=42)

        blended = {}
        all_keys = set(NEUTRAL.keys()) | set(peak_bones.keys())
        for bone_name in all_keys:
            n = NEUTRAL.get(bone_name, identity())
            p = peak_bones.get(bone_name, n)
            blended[bone_name] = tuple(slerp(n, p, t))

        # Interpolate hips location
        loc = (
            NEUTRAL_HIPS_LOC[0] + (peak_loc[0] - NEUTRAL_HIPS_LOC[0]) * t,
            NEUTRAL_HIPS_LOC[1] + (peak_loc[1] - NEUTRAL_HIPS_LOC[1]) * t,
            NEUTRAL_HIPS_LOC[2] + (peak_loc[2] - NEUTRAL_HIPS_LOC[2]) * t,
        )

        # Breathing during hold — visible chest rise/fall and weight shift
        if t > 0.2:
            hold_breath = sin(frame / TOTAL_FRAMES * 4 * pi) * t
            hold_drift = sin(frame / TOTAL_FRAMES * 2 * pi) * t
            blended["Spine1"] = tuple(apply(
                blended.get("Spine1", NEUTRAL["Spine1"]), (1, 0, 0), hold_breath * 3.0))
            blended["Spine2"] = tuple(apply(
                blended.get("Spine2", NEUTRAL["Spine2"]), (1, 0, 0), hold_breath * 2.0))
            blended["LeftShoulder"] = tuple(apply(
                blended.get("LeftShoulder", NEUTRAL["LeftShoulder"]), (1, 0, 0), hold_breath * 1.5))
            blended["RightShoulder"] = tuple(apply(
                blended.get("RightShoulder", NEUTRAL["RightShoulder"]), (1, 0, 0), hold_breath * 1.5))
            blended["Head"] = tuple(apply(
                blended.get("Head", NEUTRAL["Head"]), (1, 0, 0), hold_breath * 0.8))
            loc = (loc[0] + hold_drift * 0.002, loc[1] + hold_breath * 0.004, loc[2])

        set_pose(armature, blended, loc)
        if frame % 3 == 0 or frame == TOTAL_FRAMES - 1:
            keyframe_all(armature, frame, blended, loc)

    return action


# ── Export ────────────────────────────────────────────────────────────

def export_glb(armature, name):
    """Export current animation as GLB."""
    out_path = os.path.join(OUTPUT_DIR, name + ".glb")
    bpy.ops.object.mode_set(mode='OBJECT')
    for obj in bpy.data.objects:
        obj.select_set(obj == armature)
    bpy.context.view_layer.objects.active = armature

    bpy.ops.export_scene.gltf(
        filepath=out_path,
        export_format='GLB',
        export_animations=True,
        export_skins=False,
        export_morph=False,
        export_lights=False,
        export_cameras=False,
        export_apply=True,
        use_selection=True,
    )
    print("  EXPORTED: %s" % out_path)


def reset_pose(armature):
    """Reset all pose bones to rest position."""
    bpy.ops.object.mode_set(mode='POSE')
    for pb in armature.pose.bones:
        pb.rotation_quaternion = Quaternion((1, 0, 0, 0))
        pb.location = Vector((0, 0, 0))


# ── Main ──────────────────────────────────────────────────────────────

def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=SOURCE_GLB)

    armature = None
    for obj in bpy.data.objects:
        if obj.type == 'ARMATURE':
            armature = obj
            break

    if not armature:
        print("ERROR: No armature found in %s" % SOURCE_GLB)
        sys.exit(1)

    bpy.context.view_layer.objects.active = armature
    armature.select_set(True)

    # Clear existing animation data
    if armature.animation_data:
        armature.animation_data_clear()

    bpy.context.scene.render.fps = FPS
    bpy.context.scene.frame_start = 0
    bpy.context.scene.frame_end = TOTAL_FRAMES - 1

    print("Authoring %d poses..." % len(POSES))

    for pose_def in POSES:
        name = pose_def["name"]
        ptype = pose_def["type"]
        print("\n[%s] type=%s" % (name, ptype))

        reset_pose(armature)
        bpy.ops.object.mode_set(mode='POSE')

        if ptype == "loop":
            action = create_loop_animation(armature, pose_def)
        elif ptype == "gesture-cycle":
            action = create_gesture_cycle(armature, pose_def)
        elif ptype == "gesture-alt":
            action = create_alternating_gesture(armature, pose_def)
        elif ptype == "oscillate":
            action = create_oscillate(armature, pose_def)
        elif ptype == "oneshot":
            action = create_oneshot(armature, pose_def)
        else:
            print("  SKIP: unknown type %s" % ptype)
            continue

        export_glb(armature, name)

        # Clean up action
        armature.animation_data.action = None
        bpy.data.actions.remove(action)

    print("\nDone! Exported %d poses to %s" % (len(POSES), OUTPUT_DIR))

main()

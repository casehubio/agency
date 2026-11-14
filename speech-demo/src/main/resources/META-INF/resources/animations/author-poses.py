"""Simplest possible: IK → NLA bake → export. No envelope, no breathing."""
import bpy, sys
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=argv[0])

armature = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
bpy.context.view_layer.objects.active = armature
armature.select_set(True)
bpy.ops.object.mode_set(mode='POSE')

# Mouth target
bpy.context.view_layer.update()
head_w = armature.matrix_world @ armature.pose.bones["Head"].head
neck_w = armature.matrix_world @ armature.pose.bones["Neck"].head
mouth = (head_w + neck_w) * 0.5
mouth.x -= 0.05  # tiny bit more to the right
mouth.y -= 0.17  # forward clearance
mouth.z -= 0.02  # slightly higher — lift elbow, cover more mouth
print("Mouth: (%.3f, %.3f, %.3f)" % tuple(mouth))

# IK target
bpy.ops.object.mode_set(mode='OBJECT')
bpy.ops.object.empty_add(type='PLAIN_AXES', location=mouth)
target = bpy.context.active_object

bpy.context.view_layer.objects.active = armature
bpy.ops.object.mode_set(mode='POSE')

ik = armature.pose.bones["RightHand"].constraints.new('IK')
ik.target = target
ik.chain_count = 3
ik.iterations = 500

bpy.context.view_layer.update()
bpy.context.evaluated_depsgraph_get().update()

hand_w = armature.matrix_world @ armature.pose.bones["RightHand"].head
print("Distance: %.4f" % (hand_w - mouth).length)

# Bake to frames 0-59 (static pose held for 3 sec at 20fps)
bpy.ops.pose.select_all(action='SELECT')
bpy.context.scene.render.fps = 20
bpy.ops.nla.bake(
    frame_start=0, frame_end=59,
    only_selected=True,
    visual_keying=True,
    clear_constraints=True,
    bake_types={'POSE'},
)

from math import radians
from mathutils import Quaternion as Q

# Lift the elbow via shoulder rotation (rotator cuff)
r_shoulder = armature.pose.bones["RightShoulder"]
r_shoulder.rotation_quaternion = r_shoulder.rotation_quaternion @ Q((1, 0, 0), radians(-5))
for frame in range(60):
    r_shoulder.keyframe_insert(data_path="rotation_quaternion", frame=frame)

# Rotate palm to face the mouth — ~60° around the bone's length axis (Y)
r_hand_pb = armature.pose.bones["RightHand"]
r_hand_pb.rotation_quaternion = r_hand_pb.rotation_quaternion @ Q((0, 1, 0), radians(60))
for frame in range(60):
    r_hand_pb.keyframe_insert(data_path="rotation_quaternion", frame=frame)

# Bring fingers together (adduct) — close gaps, keep flat
# Try both Z and Y axes with stronger angles
adduction = {
    "RightHandIndex1": ("z", radians(15)),
    "RightHandRing1": ("z", radians(-15)),
    "RightHandPinky1": ("z", radians(-25)),
    "RightHandPinky2": ("z", radians(-8)),
    "RightHandThumb1": ("z", radians(-15)),
    "RightHandThumb2": ("z", radians(-8)),
}
for bone_name, (axis, angle) in adduction.items():
    pb = armature.pose.bones.get(bone_name)
    if pb:
        ax = (0, 0, 1) if axis == "z" else (0, 1, 0)
        pb.rotation_quaternion = pb.rotation_quaternion @ Q(ax, angle)
        for frame in range(60):
            pb.keyframe_insert(data_path="rotation_quaternion", frame=frame)

# Name the action
armature.animation_data.action.name = "hand-over-mouth"

# Print what we baked for the arm chain
for bn in ["RightArm", "RightForeArm", "RightHand"]:
    q = armature.pose.bones[bn].rotation_quaternion
    print("  %s: (%.5f, %.5f, %.5f, %.5f)" % (bn, q[0], q[1], q[2], q[3]))

# Export
bpy.ops.object.mode_set(mode='OBJECT')
bpy.data.objects.remove(target)
for o in bpy.data.objects: o.select_set(o == armature)
bpy.context.view_layer.objects.active = armature

bpy.ops.export_scene.gltf(
    filepath=argv[1], export_format='GLB',
    export_animations=True, export_skins=False,
    export_morph=False, export_lights=False,
    export_cameras=False, export_apply=True, use_selection=True,
)
print("Exported: %s" % argv[1])

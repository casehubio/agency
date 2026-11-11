"""Convert UniMate .npy motion output to a GLB animation file.

Produces a minimal GLB with a Mixamo-named skeleton and animation tracks
that TalkingHead's playAnimation() can load and retarget onto any avatar.

The UniMate model outputs T-pose-relative rotation deltas. This converter
rebases them into the canonical Avaturn rest-pose space that retarget.mjs
expects, so the standard retarget pipeline works without modification.

Usage:
    conda run -n unimate python unimate2glb.py <motion.npy> <output.glb> [--fps 20]

Requires the unimate conda env (for motion_utils rotation recovery).
"""

import json
import struct
import sys
import numpy as np

sys.path.insert(0, '/tmp/unimate')

from unimate.utils.motion_utils import (
    recover_unimate_anim_from_rot,
    recover_unimate_root_quat_and_pos,
    hml_rotations_to_bvh_quaternions,
)

MIXAMO_JOINT_NAMES = [
    'mixamorig:Hips', 'mixamorig:Spine', 'mixamorig:RightUpLeg',
    'mixamorig:LeftUpLeg', 'mixamorig:Spine1', 'mixamorig:RightLeg',
    'mixamorig:LeftLeg', 'mixamorig:Spine2', 'mixamorig:RightFoot',
    'mixamorig:LeftFoot', 'mixamorig:LeftShoulder', 'mixamorig:RightShoulder',
    'mixamorig:Neck', 'mixamorig:RightToeBase', 'mixamorig:LeftToeBase',
    'mixamorig:LeftArm', 'mixamorig:RightArm', 'mixamorig:Head',
    'mixamorig:LeftForeArm', 'mixamorig:RightForeArm',
    'mixamorig:LeftHand', 'mixamorig:RightHand',
]

CANONICAL_NAME = [n.replace('mixamorig:', '') for n in MIXAMO_JOINT_NAMES]

# retarget.mjs CANONICAL_REST — the Avaturn rest-pose quaternions (w,x,y,z)
CANONICAL_REST_WXYZ = {
    "Hips": [0.9999994231384476, 0.0010741148761599644, 6.537898515070053e-08, 7.022659074474357e-11],
    "Spine": [0.9998319444511763, -0.018332562694809717, -1.4544833124647894e-07, 2.6976261645093277e-10],
    "RightUpLeg": [0.0003920356017771739, 4.9438593286575395e-05, 0.003256759408127677, 0.9999946186765544],
    "LeftUpLeg": [-0.00039203576089550014, -4.952055077835167e-05, 0.0032567616062252223, 0.9999946186652781],
    "Spine1": [0.9992717837809081, -0.03815628571181615, 2.0010687323521798e-08, 5.34807122500512e-09],
    "RightLeg": [0.9997355265857409, -0.022987685898989207, 0.0006609402907891899, -7.960698692017099e-05],
    "LeftLeg": [0.9997355266049326, -0.022987686472704008, -0.0006608912898930547, 7.960712093047862e-05],
    "Spine2": [0.9983742907149431, -0.056998031890724846, 1.3546308636882866e-07, -8.861325315468363e-10],
    "RightFoot": [0.8335885491999105, 0.5523737112340288, -0.00025188760500252676, -0.0036538107149212758],
    "LeftFoot": [0.8335885543726441, 0.5523737034239063, 0.0002518875693487157, 0.0036538113016644914],
    "LeftShoulder": [0.4843526489955184, 0.5181706903502131, 0.5134387363832796, -0.48299308003518876],
    "RightShoulder": [0.4843525890882128, 0.5181706006117709, -0.5134387953473804, 0.4829931766210871],
    "Neck": [0.974930229804142, 0.2225107795455935, -2.3352293238692193e-07, -1.9647139012020554e-08],
    "RightToeBase": [0.9716236878087238, 0.23652222014073046, -0.0006644109035597298, 0.0020511526077556528],
    "LeftToeBase": [0.9716236982227819, 0.23652217735689587, 0.0006644108661216591, -0.002051152932444189],
    "LeftArm": [0.9964608827384491, -0.003089573263103852, 0.08018148459223169, -0.025041829737156785],
    "RightArm": [0.9964608693782484, -0.003089570938390283, -0.08018164882954634, 0.025041835809991725],
    "Head": [0.993123804804428, -0.11706881878098431, 9.438845082943482e-09, 3.72654613409334e-08],
    "LeftForeArm": [0.9999234962951223, -2.6826767953574183e-07, 1.2816740072681333e-07, 0.012369379808617674],
    "RightForeArm": [0.9999234962561284, -2.706717415294191e-07, 6.0675494372234465e-09, -0.012369382961452784],
    "LeftHand": [0.9999447735952517, -0.0012910117424963744, 0.002537815819541244, 0.010116448936771993],
    "RightHand": [0.9999447735796301, -0.0012910114923260908, -0.002537815924782367, -0.010116450486313036],
}


def quat_multiply(a, b):
    """Hamilton product of two quaternions in (w,x,y,z) format."""
    aw, ax, ay, az = a
    bw, bx, by, bz = b
    return np.array([
        aw*bw - ax*bx - ay*by - az*bz,
        aw*bx + ax*bw + ay*bz - az*by,
        aw*by - ax*bz + ay*bw + az*bx,
        aw*bz + ax*by - ay*bx + az*bw,
    ])


def load_motion(npy_path, cond_path):
    motion = np.load(npy_path)
    cond = np.load(cond_path, allow_pickle=True).item()['mixamo']
    parents = cond['parents']
    offsets = cond['offsets']

    # Get T-pose-relative BVH rotation deltas
    deltas = hml_rotations_to_bvh_quaternions(motion[:, :, 3:9], parents)
    delta_qs = deltas.qs.astype(np.float32)  # (T, J, 4) in w,x,y,z

    # Recover root trajectory
    r_rot_quat, r_pos = recover_unimate_root_quat_and_pos(motion[:, 0, :])

    # Rebase deltas into canonical rest-pose space:
    # canonical_rot[t,j] = canonical_rest[j] × delta[t,j]
    n_frames, n_joints = delta_qs.shape[:2]
    rebased = np.zeros_like(delta_qs)

    for j in range(n_joints):
        canon_name = CANONICAL_NAME[j]
        rest_q = CANONICAL_REST_WXYZ.get(canon_name)
        if rest_q is None:
            rebased[:, j] = delta_qs[:, j]
            continue
        rest_q = np.array(rest_q, dtype=np.float32)
        for t in range(n_frames):
            rebased[t, j] = quat_multiply(rest_q, delta_qs[t, j])

    # Compose root orient (facing direction) with rebased root rotation
    orient_qs = r_rot_quat.qs.astype(np.float32)  # (T, 4) w,x,y,z
    for t in range(n_frames):
        rebased[t, 0] = quat_multiply(orient_qs[t], rebased[t, 0])

    return rebased, r_pos.astype(np.float32), parents, offsets


def build_glb(rots_wxyz, root_pos, parents, offsets, fps=20, clip_name='animation'):
    n_frames, n_joints = rots_wxyz.shape[:2]
    duration = (n_frames - 1) / fps
    times = np.linspace(0, duration, n_frames, dtype=np.float32)

    # Convert (w,x,y,z) → (x,y,z,w) for GLTF
    rots_xyzw = np.zeros_like(rots_wxyz)
    rots_xyzw[..., 0] = rots_wxyz[..., 1]
    rots_xyzw[..., 1] = rots_wxyz[..., 2]
    rots_xyzw[..., 2] = rots_wxyz[..., 3]
    rots_xyzw[..., 3] = rots_wxyz[..., 0]

    offsets_f32 = offsets.astype(np.float32)

    # Set node rest rotations to CANONICAL_REST (so retarget.mjs sees correct source)
    node_rest_xyzw = []
    for j in range(n_joints):
        canon_name = CANONICAL_NAME[j]
        rest_q = CANONICAL_REST_WXYZ.get(canon_name, [1, 0, 0, 0])
        w, x, y, z = rest_q
        node_rest_xyzw.append([x, y, z, w])

    buffer_parts = []
    accessor_defs = []
    buffer_view_defs = []

    def add_accessor(data, component_type, atype):
        data = np.ascontiguousarray(data)
        raw = data.tobytes()
        pad = (4 - len(raw) % 4) % 4
        raw += b'\x00' * pad
        bv_idx = len(buffer_view_defs)
        offset = sum(len(p) for p in buffer_parts)
        buffer_view_defs.append({
            'buffer': 0, 'byteOffset': offset, 'byteLength': data.nbytes,
        })
        buffer_parts.append(raw)
        acc = {
            'bufferView': bv_idx, 'componentType': component_type,
            'count': data.shape[0], 'type': atype,
        }
        if atype == 'SCALAR':
            acc['min'] = [float(data.min())]
            acc['max'] = [float(data.max())]
        elif atype in ('VEC3', 'VEC4'):
            acc['min'] = data.min(axis=0).tolist()
            acc['max'] = data.max(axis=0).tolist()
        accessor_defs.append(acc)
        return len(accessor_defs) - 1

    time_acc = add_accessor(times, 5126, 'SCALAR')
    rot_accs = [add_accessor(rots_xyzw[:, j, :], 5126, 'VEC4') for j in range(n_joints)]
    pos_acc = add_accessor(root_pos, 5126, 'VEC3')

    nodes = []
    for j in range(n_joints):
        node = {'name': MIXAMO_JOINT_NAMES[j],
                'rotation': node_rest_xyzw[j],
                'translation': offsets_f32[j].tolist()}
        children = [k for k in range(n_joints) if parents[k] == j]
        if children:
            node['children'] = children
        nodes.append(node)
    root_children = [j for j in range(n_joints) if parents[j] == -1]
    nodes.append({'name': 'Armature', 'children': root_children})

    samplers = []
    channels = []
    for j in range(n_joints):
        samplers.append({'input': time_acc, 'output': rot_accs[j], 'interpolation': 'LINEAR'})
        channels.append({'sampler': len(samplers) - 1, 'target': {'node': j, 'path': 'rotation'}})
    samplers.append({'input': time_acc, 'output': pos_acc, 'interpolation': 'LINEAR'})
    channels.append({'sampler': len(samplers) - 1, 'target': {'node': 0, 'path': 'translation'}})

    full_buffer = b''.join(buffer_parts)
    gltf = {
        'asset': {'version': '2.0', 'generator': 'unimate2glb'},
        'scene': 0,
        'scenes': [{'nodes': [len(nodes) - 1]}],
        'nodes': nodes,
        'skins': [{'joints': list(range(n_joints)), 'skeleton': root_children[0], 'name': 'Armature'}],
        'animations': [{'name': clip_name, 'channels': channels, 'samplers': samplers}],
        'accessors': accessor_defs,
        'bufferViews': buffer_view_defs,
        'buffers': [{'byteLength': len(full_buffer)}],
    }
    return gltf, full_buffer


def write_glb(gltf, binary_buffer, output_path):
    json_bytes = json.dumps(gltf, separators=(',', ':')).encode('utf-8')
    json_pad = (4 - len(json_bytes) % 4) % 4
    json_bytes += b' ' * json_pad
    bin_pad = (4 - len(binary_buffer) % 4) % 4
    binary_buffer += b'\x00' * bin_pad
    total = 12 + 8 + len(json_bytes) + 8 + len(binary_buffer)
    with open(output_path, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, total))
        f.write(struct.pack('<II', len(json_bytes), 0x4E4F534A))
        f.write(json_bytes)
        f.write(struct.pack('<II', len(binary_buffer), 0x004E4942))
        f.write(binary_buffer)
    print(f'Written {output_path} ({total:,} bytes)')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='Convert UniMate .npy to GLB')
    parser.add_argument('npy', help='Path to motion .npy file')
    parser.add_argument('output', help='Output .glb path')
    parser.add_argument('--fps', type=int, default=20)
    parser.add_argument('--name', default='animation', help='Animation clip name')
    parser.add_argument('--cond', default='/tmp/unimate/dataset/features/mixamo/cond.npy')
    args = parser.parse_args()
    rots, pos, parents, offsets = load_motion(args.npy, args.cond)
    gltf, buf = build_glb(rots, pos, parents, offsets, fps=args.fps, clip_name=args.name)
    write_glb(gltf, buf, args.output)

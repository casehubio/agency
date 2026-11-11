"""Convert UniMate .npy motion output to a GLB animation file.

Produces a minimal GLB with a Mixamo-named skeleton and animation tracks
that TalkingHead's playAnimation() can load and retarget onto any avatar.

Usage:
    conda run -n unimate python unimate2glb.py <motion.npy> <output.glb> [--fps 20]

Requires the unimate conda env (for motion_utils rotation recovery).
"""

import json
import struct
import sys
import os
import numpy as np

sys.path.insert(0, '/tmp/unimate')

from unimate.utils.motion_utils import recover_unimate_anim_from_rot

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


def load_motion(npy_path, cond_path):
    motion = np.load(npy_path)
    cond = np.load(cond_path, allow_pickle=True).item()['mixamo']
    parents = cond['parents']
    offsets = cond['offsets']
    anim = recover_unimate_anim_from_rot(motion, parents, offsets)
    return anim, parents, offsets


def build_glb(anim, parents, offsets, fps=20, clip_name='animation'):
    n_frames = anim.rotations.qs.shape[0]
    n_joints = len(MIXAMO_JOINT_NAMES)
    duration = (n_frames - 1) / fps
    times = np.linspace(0, duration, n_frames, dtype=np.float32)

    rots_wxyz = anim.rotations.qs.astype(np.float32)
    orients = anim.orients.qs.astype(np.float32)
    for t in range(n_frames):
        ow, ox, oy, oz = orients[t]
        rw, rx, ry, rz = rots_wxyz[t, 0]
        rots_wxyz[t, 0] = [
            ow*rw - ox*rx - oy*ry - oz*rz,
            ow*rx + ox*rw + oy*rz - oz*ry,
            ow*ry - ox*rz + oy*rw + oz*rx,
            ow*rz + ox*ry - oy*rx + oz*rw,
        ]

    rots_xyzw = np.zeros_like(rots_wxyz)
    rots_xyzw[..., 0] = rots_wxyz[..., 1]
    rots_xyzw[..., 1] = rots_wxyz[..., 2]
    rots_xyzw[..., 2] = rots_wxyz[..., 3]
    rots_xyzw[..., 3] = rots_wxyz[..., 0]

    root_pos = anim.positions[:, 0, :].astype(np.float32)
    offsets_f32 = offsets.astype(np.float32)

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
        node = {'name': MIXAMO_JOINT_NAMES[j], 'rotation': [0, 0, 0, 1],
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
    anim, parents, offsets = load_motion(args.npy, args.cond)
    gltf, buf = build_glb(anim, parents, offsets, fps=args.fps, clip_name=args.name)
    write_glb(gltf, buf, args.output)

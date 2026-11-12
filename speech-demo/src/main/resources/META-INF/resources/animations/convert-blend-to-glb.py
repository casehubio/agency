#!/usr/bin/env python3
"""Batch-convert Mesh2Motion .blend files to GLB for TalkingHead playAnimation().

Usage:
    blender --background --python convert-blend-to-glb.py -- <input-dir> <output-dir>

Example:
    blender --background --python convert-blend-to-glb.py -- \
        /tmp/mesh2motion-assets/rigs/human ./

Converts all .blend files in subdirectories (emotes/, emotions/, idles/)
to animation-only GLB files suitable for Three.js FBXLoader/GLTFLoader.

License: CC0 (Mesh2Motion assets are CC0 1.0 public domain)
"""
import sys
import os
from pathlib import Path

try:
    import bpy
except ImportError:
    print("This script must be run inside Blender:")
    print("  blender --background --python convert-blend-to-glb.py -- <input-dir> <output-dir>")
    sys.exit(1)

argv = sys.argv
if "--" in argv:
    argv = argv[argv.index("--") + 1:]
else:
    print("Usage: blender --background --python convert-blend-to-glb.py -- <input-dir> <output-dir>")
    sys.exit(1)

input_dir = Path(argv[0])
output_dir = Path(argv[1])
output_dir.mkdir(parents=True, exist_ok=True)

subdirs = ["emotes", "emotions", "idles", "activities"]
converted = 0
skipped = 0

for subdir in subdirs:
    src = input_dir / subdir
    if not src.exists():
        continue
    for blend_file in sorted(src.glob("*.blend")):
        name = blend_file.stem.replace("animation-human-", "")
        out_path = output_dir / f"{name}.glb"
        if out_path.exists():
            print(f"  SKIP {name} (already exists)")
            skipped += 1
            continue

        print(f"  Converting {blend_file.name} -> {name}.glb")

        bpy.ops.wm.open_mainfile(filepath=str(blend_file))

        bpy.ops.export_scene.gltf(
            filepath=str(out_path),
            export_format='GLB',
            export_animations=True,
            export_skins=False,
            export_morph=False,
            export_lights=False,
            export_cameras=False,
            export_apply=True,
        )
        converted += 1

print(f"\nDone: {converted} converted, {skipped} skipped")

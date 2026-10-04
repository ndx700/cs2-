#!/usr/bin/env python3
"""Check the actual packaged scene, including AAPT's expanded .gz assets."""
import array
import gzip
import hashlib
import json
import math
import struct
import sys
import zipfile


def validate(apk):
    with zipfile.ZipFile(apk) as archive:
        manifest = json.loads(archive.read("assets/maps/dust2/mobile/manifest.json"))
        names = set(archive.namelist())
        expanded = 0
        for part in manifest["parts"]:
            name = "assets/" + part["asset"]
            if name not in names and name.endswith(".gz"):
                name = name[:-3]
                expanded += 1
            data = archive.read(name)
            if data[:2] == b"\x1f\x8b":
                data = gzip.decompress(data)
            magic, vertices, indices, stride = struct.unpack_from(">4I", data)
            assert (magic, vertices, indices, stride) == (
                0x44324D31, part["vertexCount"], part["indexCount"], 36
            ), name
            assert len(data) == 16 + part["bytes"] == 16 + vertices * 36 + indices * 2, name
            for values in struct.iter_unpack("<3f8x2f8x", data[16:16 + vertices * 36]):
                assert all(math.isfinite(v) for v in values), name
            index_values = array.array("H", data[16 + vertices * 36:])
            if sys.byteorder != "little":
                index_values.byteswap()
            assert indices >= 3 and indices % 3 == 0 and max(index_values) < vertices, name
        for texture in manifest["textures"]:
            data = archive.read("assets/" + texture["asset"])
            assert hashlib.sha256(data).hexdigest() == texture["sha256"], texture["asset"]
        print(f"PASS packaged map: {len(manifest['parts'])} chunks, "
              f"{len(manifest['textures'])} textures, {expanded} expanded gzip paths")


if __name__ == "__main__":
    validate(sys.argv[1])

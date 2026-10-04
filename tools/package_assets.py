#!/usr/bin/env python3
"""Repack all current assets into reproducible 3 MiB Git-stored volumes."""
import argparse
import hashlib
import json
import tempfile
import zipfile
from pathlib import Path


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True, help='Asset version identifier')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    assets = root / 'app/src/main/assets'
    output = root / 'asset-packs'
    output.mkdir(exist_ok=True)
    manifest = {'schema': 1, 'version': args.version, 'part_bytes': 3 * 1024 * 1024, 'parts': [], 'files': []}
    with tempfile.TemporaryDirectory() as tmp:
        archive = Path(tmp) / 'assets.zip'
        with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for path in sorted(p for p in assets.rglob('*') if p.is_file()):
                name = path.relative_to(assets).as_posix()
                data = path.read_bytes()
                info = zipfile.ZipInfo(name, date_time=(2026, 10, 4, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                z.writestr(info, data)
                manifest['files'].append({'path': name, 'bytes': len(data), 'sha256': sha(data)})
        manifest['archive_sha256'] = sha(archive.read_bytes())
        with archive.open('rb') as source:
            number = 0
            for data in iter(lambda: source.read(manifest['part_bytes']), b''):
                number += 1
                name = f'assets.zip.{number:03d}'
                (output / name).write_bytes(data)
                manifest['parts'].append({'name': name, 'bytes': len(data), 'sha256': sha(data)})
    active = {x['name'] for x in manifest['parts']}
    for old in output.glob('assets.zip.*'):
        if old.name not in active:
            old.unlink()
    (output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(f"Packed {len(manifest['files'])} assets into {len(manifest['parts'])} parts")


if __name__ == '__main__':
    main()

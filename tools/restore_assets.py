#!/usr/bin/env python3
"""Restore and verify the complete checked-in map asset bundle. No network required."""
import argparse
import hashlib
import json
import os
import shutil
import tempfile
import zipfile
from pathlib import Path, PurePosixPath


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--force', action='store_true', help='Replace locally modified baseline assets')
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = json.loads((root / 'asset-packs/manifest.json').read_text())
    target = root / 'app/src/main/assets'
    files = manifest['files']
    expected = {}
    for item in files:
        name = item['path']
        rel = PurePosixPath(name)
        if rel.is_absolute() or '..' in rel.parts or str(rel) != name or name in expected:
            raise SystemExit('Invalid asset path: ' + name)
        path = target / name
        if not path.resolve().is_relative_to(target.resolve()):
            raise SystemExit('Asset path escapes target: ' + name)
        if path.exists() and digest(path) != item['sha256'] and not args.force:
            raise SystemExit('Locally modified asset; repack or use --force: ' + name)
        expected[name] = item
    work = root / 'build/asset-restore'
    work.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=work) as tmp:
        archive = Path(tmp) / 'assets.zip'
        with archive.open('wb') as output:
            for part in manifest['parts']:
                path = root / 'asset-packs' / part['name']
                if path.stat().st_size != part['bytes'] or digest(path) != part['sha256']:
                    raise SystemExit('Corrupt asset part: ' + part['name'])
                with path.open('rb') as source:
                    shutil.copyfileobj(source, output)
        if digest(archive) != manifest['archive_sha256']:
            raise SystemExit('Asset archive checksum mismatch')
        with zipfile.ZipFile(archive) as bundle:
            names = bundle.namelist()
            if len(names) != len(set(names)) or set(names) != set(expected):
                raise SystemExit('Archive file list mismatch')
            for name in names:
                item = expected[name]
                data = bundle.read(name)
                if len(data) != item['bytes'] or hashlib.sha256(data).hexdigest() != item['sha256']:
                    raise SystemExit('Asset checksum mismatch: ' + name)
                path = target / name
                path.parent.mkdir(parents=True, exist_ok=True)
                if path.exists() and digest(path) == item['sha256']:
                    continue
                staged = path.with_name(path.name + '.restore-tmp')
                staged.write_bytes(data)
                os.replace(staged, path)
    print(f"Assets verified/restored: {len(files)} files, {sum(x['bytes'] for x in files)} bytes")


if __name__ == '__main__':
    main()

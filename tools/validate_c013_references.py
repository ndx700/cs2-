#!/usr/bin/env python3
"""Validate the C013 partial resource pack without promoting it to a course."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path


def read(path):
    return json.loads(path.read_text())


def check(root):
    folder = root / "docs/calibration/C013"
    candidates = read(root / "docs/materials/dust2-v1-candidates.json")["entries"]
    if [entry["id"] for entry in candidates] != [f"D2-{i:03}" for i in range(1, 15)]:
        raise ValueError("C012 stable identities changed")
    calibration = read(folder / "calibration-records.json")
    if calibration["schema"] != "c013-resource-measurements-v1":
        raise ValueError("unexpected resource schema")
    records = calibration["records"]
    if {r["id"] for r in records} != {"D2-001", "D2-014", "D2-009", "D2-010"} or len(records) != 4:
        raise ValueError("priority record identities changed")
    screenshots = read(folder / "screenshot-index.json")["screenshots"]
    image_ids = {x["id"] for x in screenshots}
    source_records = read(folder / "source-pages.json")["records"]
    source_ids = {x["id"] for x in source_records}
    for r in records:
        if r["id"] not in source_ids or not set(r["screenshots"]).issubset(image_ids):
            raise ValueError("source/screenshot reference missing")
        if r["status"] != "PENDING_CALIBRATION" or r["formalImportAllowed"]:
            raise ValueError("unmeasured pack cannot enable formal import")
        for key in ("standingFeetDisplay", "eyeDisplay", "aimPointDisplay", "landingDisplay", "trajectory", "coverageDisplay", "fireRegions", "lateIgnitionAcceptancePointDisplay", "heHole"):
            if r[key] is not None:
                raise ValueError("new measured value needs explicit evidence: " + key)
        if any(s["ageSeconds"] is not None or s["measurementEvidence"] is not None for s in r["effectStages"]):
            raise ValueError("uncalibrated effect age must remain null")
        versions = r["versions"]
        for name, path in (("mobileManifestSha256", "app/src/main/assets/maps/dust2/mobile/manifest.json"), ("assetPackManifestSha256", "asset-packs/manifest.json")):
            if hashlib.sha256((root / path).read_bytes()).hexdigest() != versions[name]:
                raise ValueError("resource version mismatch: " + name)
    adapter = read(folder / "app-adapter-report.json")
    if adapter["runtimeImportAllowed"] or len(adapter["drafts"]) != 4:
        raise ValueError("pending adapter cannot enable playback")
    for relative in adapter["drafts"]:
        path = (folder / relative).resolve()
        if not path.is_relative_to(folder.resolve()):
            raise ValueError("draft path outside calibration folder")
        draft = read(path)
        if draft["schema"] != "c013-course-fx-v1" or draft["status"] != "PENDING_CALIBRATION" or draft["importable"]:
            raise ValueError("pending APP draft identity/status invalid")
        if draft["mapVersion"] != adapter["mapVersion"] or draft["coordinateSpace"] != "display-m-y-up-v1":
            raise ValueError("APP coordinate/version mismatch")
        for key in ("foot", "eye", "aim", "bodyYaw", "aimFov", "cameras", "throwHint", "begin", "aimAt", "throwAt", "duration", "path", "smoke", "fire", "he"):
            if draft[key] is not None:
                raise ValueError("pending runtime value requires calibration: " + key)
    aim = read(folder / "aim-texture-checks.json")
    if aim["allCoursesCalibrated"] or [r["id"] for r in aim["records"]] != [e["id"] for e in candidates]:
        raise ValueError("aim acceptance status or IDs invalid")
    for row in aim["records"]:
        if row["mapVersion"] != adapter["mapVersion"] or any(v is not None for v in row["acceptance"].values()):
            raise ValueError("unverified aim acceptance cannot pass")
        for reference in row["referenceImages"]:
            original = next((x for x in screenshots if x["id"] == reference["id"]), None)
            if original is None or original["courseId"] != row["id"] or original["sha256"] != reference["sha256"] or original["path"] != reference["path"]:
                raise ValueError("aim screenshot identity/hash mismatch")
    clips = read(folder / "clip-observations.json")["clips"]
    for item in [*screenshots, *[{"path": c["observationStrip"], "sha256": c["observationStripSha256"]} for c in clips]]:
        path = root / item["path"]
        if not path.resolve().is_relative_to(root.resolve()) or hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]:
            raise ValueError("screenshot missing/changed/outside repository")
    audit = read(folder / "collision-audit.json")
    if sum(x["triangles"] for x in audit["collisionAttributes"]) != audit["triangleCount"] or {x["index"] for x in audit["collisionAttributes"]} != set(range(6)):
        raise ValueError("full collision layer coverage lost")
    if audit["checks"]["mixedSurfaceShapes"] != [10093, 10094]:
        raise ValueError("world surface override evidence changed")
    archive = folder / "collision" / audit["supplementalArchive"]["name"]
    if hashlib.sha256(archive.read_bytes()).hexdigest() != audit["supplementalArchive"]["sha256"]:
        raise ValueError("collision query archive hash mismatch")
    with zipfile.ZipFile(archive) as bundle:
        names = bundle.namelist()
        if len(names) != 6 or set(names) != {"manifest.json", *audit["rawFiles"]}:
            raise ValueError("collision query archive must preserve all streams")
        if hashlib.sha256(bundle.read("manifest.json")).hexdigest() != audit["checks"]["rawManifestSha256"]:
            raise ValueError("raw manifest changed")
        for name, spec in audit["rawFiles"].items():
            info = bundle.getinfo(name)
            if info.file_size != spec["bytes"] or hashlib.sha256(bundle.read(name)).hexdigest() != spec["sha256"]:
                raise ValueError("collision stream changed: " + name)
    print(f"PASS: 14 stable IDs/aim records, four partial records/APP drafts, {len(screenshots)} screenshots, four clip strips, version hashes and complete collision archive; formal import remains disabled")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    check(parser.parse_args().root.resolve())


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Export resource observations as APP v1 drafts. Never infer runtime values.

APP's coordinate name contains 'm', while R002 certifies only the historical
0.0254 display convention. This is an alias at the interface, not a second
conversion or a claim about Valve physical units.
"""
import argparse
import hashlib
import json
from pathlib import Path

CONTRACT_HEAD = "516f2ce41ccd55a7aac441a2ee52d80d550e71a1"
CONTRACT_BLOB = "f168be008e7c60334665b1a600dd13f5c9c84dd4"
CONTRACT_PATH = "docs/contracts/C013-course-fx-v1.md"
PRIORITY = {"D2-001", "D2-014", "D2-009", "D2-010"}


def make_pending(record, actual_map_hash):
    if record["id"] not in PRIORITY:
        raise ValueError("unsupported priority course")
    if record["status"] != "PENDING_CALIBRATION" or record["formalImportAllowed"]:
        raise ValueError("adapter only accepts pending evidence")
    if record["versions"]["mobileManifestSha256"] != actual_map_hash:
        raise ValueError("map version mismatch")
    return {
        "schema": "c013-course-fx-v1", "mapId": "dust2",
        "lessonId": record["id"], "title": record["purpose"],
        "relatedIds": ["D2-014"] if record["id"] == "D2-001" else
                      ["D2-001"] if record["id"] == "D2-014" else [],
        "status": "PENDING_CALIBRATION", "importable": False,
        "mapVersion": actual_map_hash, "coordinateSpace": "display-m-y-up-v1",
        "timeOrigin": "throw-release-seconds",
        "evidence": {"url": record["teachingSource"], "gameBuild": None,
                     "releaseVideoSeconds": None},
        "teachingSource": record["teachingSource"],
        "effectSource": record["effectSourceInC012"],
        "referenceSegment": record["C012Segment"],
        **{key: None for key in ("foot", "eye", "aim", "bodyYaw", "aimFov",
                                "cameras", "throwHint", "begin", "aimAt", "throwAt",
                                "duration", "path", "smoke", "fire", "he")},
        "resourceObservations": {
            "sourceRecord": "docs/calibration/C013/calibration-records.json#" + record["id"],
            "positionSemantics": record["consolePositionSemantics"],
            "sourceConsolePosition": record["consolePositionSource"],
            "displayConsolePosition": record["consolePositionDisplay"],
            "sourceAnglesPitchYawRoll": record["anglesSourcePitchYawRollDegrees"],
            "sourceDeclaredThrowFlags": record["throwFlagsSourceDeclared"],
            "screenshotIds": record["screenshots"],
            "coordinateAlias": "dust2_display_y_up_v1 -> display-m-y-up-v1; preserve numeric values; no second transform; physical-unit certification pending",
            "effectStages": record["effectStages"],
        },
        "aimTextureAcceptance": "docs/calibration/C013/aim-texture-checks.json#" + record["id"],
        "missing": ["validated feet/eye/aim and camera FOV", "verified throw/path/landing",
                    "continuous release-relative smoke/fire/HE measurements",
                    "CS2 game/map version and equivalent view reference",
                    "APP phone screenshots with hints visible and hidden"],
    }


def export(root):
    folder = root / "docs/calibration/C013"
    records = json.loads((folder / "calibration-records.json").read_text())["records"]
    manifest_hash = hashlib.sha256((root / "app/src/main/assets/maps/dust2/mobile/manifest.json").read_bytes()).hexdigest()
    output = folder / "app-pending"
    output.mkdir(exist_ok=True)
    for record in records:
        (output / (record["id"] + ".json")).write_text(
            json.dumps(make_pending(record, manifest_hash), ensure_ascii=False, indent=2) + "\n")
    report = {
        "schema": "c013-resource-app-adapter-v1", "status": "PENDING_CALIBRATION",
        "appContract": {"path": CONTRACT_PATH, "headSha": CONTRACT_HEAD, "blobSha": CONTRACT_BLOB},
        "mapVersion": manifest_hash, "drafts": ["app-pending/" + r["id"] + ".json" for r in records],
        "runtimeImportAllowed": False,
        "reason": "Contract-aligned pending documents; APP parser rejects importable=false before runtime fields. This is not a playable or calibrated course.",
    }
    (folder / "app-adapter-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(f"Exported {len(records)} APP v1 drafts; playback remains disabled")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    export(parser.parse_args().root.resolve())

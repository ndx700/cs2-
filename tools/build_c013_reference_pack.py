#!/usr/bin/env python3
"""Build a partial resource-side evidence pack, never formal APP courses.

Input source-pages.json is produced by collect_c013_references.py. Screenshots
and encoded-video observations retain their own clocks; real effect ages stay
null until a continuous, versioned CS2 reference is measured.
"""
import argparse
import hashlib
import io
import json
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw

from dust2_collision_query import source_to_display

PRIORITY = ("D2-001", "D2-014", "D2-009", "D2-010")
SAMPLES = {
    "D2-001": [3.5, 4.0, 8.0, 12.5, 14.0],
    "D2-014": [2.0, 5.5, 6.0, 7.0, 8.5],
    "D2-009": [5.0, 5.5, 6.5, 7.5, 9.0],
    "D2-010": [5.0, 5.5, 6.5, 7.5, 8.5],
}
PHASES = {
    "SMOKE": ["throw_release", "landing", "first_smoke", "basic_formed", "fully_formed", "dissipation_begin", "dissipation_end"],
    "HE": ["smoke_fully_formed", "he_release", "explosion", "hole_open", "refill_begin", "refill_end"],
    "MOLOTOV": ["throw_release", "impact", "first_ignition", "late_region_ignition", "final_coverage", "extinguish_begin", "extinguish_end"],
}
OBSERVATIONS = {
    "D2-001": "Clip contains throw and multiple camera cuts; full smoke lifespan is not shown. No release-to-effect age is measured across a cut.",
    "D2-014": "Clip shows HE throw and a camera cut. Continuous already-formed-smoke, hole-opening and refill evidence is insufficient.",
    "D2-009": "Overview samples at clip 5.5 and 7.5 seconds show a larger later fire footprint; extinction is not shown. World-space regions and game-clock delay remain unmeasured.",
    "D2-010": "Overview samples at clip 5.5 and 7.5 seconds show a larger later fire footprint around car; extinction is not shown. World-space regions and game-clock delay remain unmeasured.",
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def observation_strip(video, identity, output):
    times = SAMPLES[identity]
    strip = Image.new("RGB", (640 * len(times), 382), "#20242a")
    draw = ImageDraw.Draw(strip)
    for index, time in enumerate(times):
        frame = subprocess.check_output(["ffmpeg", "-v", "error", "-ss", str(time), "-i", str(video),
                                         "-frames:v", "1", "-vf", "scale=640:360",
                                         "-f", "image2pipe", "-vcodec", "mjpeg", "pipe:1"])
        with Image.open(io.BytesIO(frame)) as image:
            strip.paste(image, (index * 640, 0))
        draw.text((index * 640 + 8, 364), f"clip {time:.2f}s (source clock)", fill="white")
    dest = output / "screenshots" / f"{identity}-observations.jpg"
    dest.parent.mkdir(parents=True, exist_ok=True)
    strip.save(dest, quality=85)
    return dest


def build(root, media_dir, output):
    candidates = json.loads((root / "docs/materials/dust2-v1-candidates.json").read_text())
    by_id = {item["id"]: item for item in candidates["entries"]}
    sources = json.loads((output / "source-pages.json").read_text())
    mobile_path = root / "app/src/main/assets/maps/dust2/mobile/manifest.json"
    mobile = json.loads(mobile_path.read_text())
    asset_manifest_path = root / "asset-packs/manifest.json"
    versions = {"appBaseline": "v0.6.0-test2", "mobileSchema": mobile["schema"],
                "mobileManifestSha256": sha(mobile_path),
                "assetPackVersion": json.loads(asset_manifest_path.read_text())["version"],
                "assetPackManifestSha256": sha(asset_manifest_path),
                "worldCollisionResource": "R002", "sourceGameBuild": None, "sourceMapBuild": None}
    screenshots, clips, records = [], [], []
    for source in sources["records"]:
        identity = source["id"]
        candidate = by_id[identity]
        source_screens = []
        guides_seen = 0
        for index, item in enumerate(source["media"]):
            p = media_dir / item.get("inspection_file", "missing")
            if not p.is_file() or item.get("download_status") != "FETCHED":
                continue
            if sha(p) != item["sha256"]:
                raise ValueError("downloaded media changed: " + str(p))
            if item["kind"] == "img":
                role = {"section-land": "landing_reference", "section-affected": "coverage_reference"}.get(item["section"], "reference")
                if item["section"] == "section-guide":
                    role = "stand_reference" if identity == "D2-001" and guides_seen == 0 else "aim_reference"
                    guides_seen += 1
                dest = output / "screenshots" / f"{identity}-{index:02d}-{role}.jpg"
                dest.parent.mkdir(parents=True, exist_ok=True)
                with Image.open(p) as image:
                    image = image.convert("RGB")
                    image.thumbnail((1280, 720), Image.Resampling.LANCZOS)
                    image.save(dest, quality=86, optimize=True)
                    width, height = image.size
                reference = {"id": f"{identity}-image-{index:02d}", "courseId": identity, "role": role,
                             "sourceUrl": item["url"], "sourceSha256": item["sha256"],
                             "path": dest.relative_to(root).as_posix(), "sha256": sha(dest),
                             "width": width, "height": height, "mapBuild": None, "gameBuild": None,
                             "source": "GetReplay linked screenshot; not a calibrated world-space point"}
                screenshots.append(reference)
                source_screens.append(reference["id"])
            elif item["section"] == "section-demo":
                strip = observation_strip(p, identity, output)
                probe = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration:stream=codec_type,width,height,r_frame_rate", "-of", "json", str(p)]))
                streams = [s for s in probe["streams"] if s["codec_type"] == "video"]
                clips.append({"id": identity + "-demo", "courseId": identity, "sourceUrl": item["url"],
                              "sourceSha256": item["sha256"], "bytes": item["bytes"],
                              "encodedDurationSeconds": float(probe["format"]["duration"]), "video": streams[0],
                              "clock": "ENCODED_CLIP_SECONDS_NOT_CERTIFIED_GAME_TIME",
                              "fullLifecycleObserved": False, "videoNotBundledInAPK": True,
                              "sampleTimesSeconds": SAMPLES[identity], "observation": OBSERVATIONS[identity],
                              "observationStrip": strip.relative_to(root).as_posix(),
                              "observationStripSha256": sha(strip),
                              "mapBuild": None, "gameBuild": None, "matchToC012NartVariant": "UNCONFIRMED"})
        commands = source["source_commands"]
        command = commands[0] if len(commands) == 1 else None
        record = {"id": identity, "type": candidate["type"], "purpose": candidate["purpose"],
                  "status": "PENDING_CALIBRATION", "formalImportAllowed": False,
                  "sourcePageFetched": source["status"] == "PAGE_FETCHED",
                  "teachingSource": source["teaching_source_url"],
                  "effectSourceInC012": source["effect_source_url_in_C012"],
                  "C012Segment": source["reference_segment_in_C012"], "versions": versions,
                  "consolePositionSource": command["position_source_xyz"] if command else None,
                  "consolePositionDisplay": source_to_display(command["position_source_xyz"]).tolist() if command else None,
                  "consolePositionSemantics": "UNCONFIRMED_PLAYER_OR_EYE_POSITION_NOT_FEET",
                  "anglesSourcePitchYawRollDegrees": command["angles_source_pitch_yaw_roll_degrees"] if command else None,
                  "commandStatus": command["status"] if command else "NOT_AVAILABLE_ON_PAGE",
                  "throwFlagsSourceDeclared": source["throw_flags_from_page_hint"],
                  "standingFeetDisplay": None, "eyeDisplay": None, "aimPointDisplay": None,
                  "landingDisplay": None, "trajectory": None, "coverageDisplay": None,
                  "effectTimeZero": {"event": "throw_release", "clipTimeSeconds": None, "status": "PENDING_CONTINUITY_AND_GAME_CLOCK_CHECK"},
                  "effectStages": [{"event": event, "ageSeconds": None, "sourceClipTimeSeconds": None,
                                    "measurementEvidence": None, "status": "PENDING_CALIBRATION"}
                                   for event in PHASES[candidate["type"]]],
                  "fireRegions": None, "lateIgnitionAcceptancePointDisplay": None,
                  "heHole": None, "screenshots": source_screens, "clipObservationId": identity + "-demo"}
        records.append(record)
    write(output / "screenshot-index.json", {"schema": "c013-screenshots-v1", "screenshots": screenshots})
    write(output / "clip-observations.json", {"schema": "c013-clip-observations-v1", "clips": clips})
    write(output / "calibration-records.json", {"schema": "c013-resource-measurements-v1", "owner": "C013-RES",
          "appContractAdapter": {"status": "BLOCKED", "reason": "APP-owned C013-course-fx-v1.md absent from opening main; these are source records, not final course import data"},
          "coordinateSpace": "dust2_display_y_up_v1", "lengthUnit": "display_unit",
          "sourceToDisplay": "[x*0.0254, z*0.0254, -y*0.0254]; apply once",
          "worldCollisionIncludesEntityPhysics": False, "records": records,
          "interaction": {"id": "D2-001-D2-014", "smokeId": "D2-001", "heId": "D2-014",
                          "status": "PENDING_CONTINUOUS_REFERENCE", "smokeReleaseCourseSeconds": None,
                          "heReleaseCourseSeconds": None, "heExplosionCourseSeconds": None,
                          "holeOpenCourseSeconds": None, "refillCompleteCourseSeconds": None}})
    texture_by_path = {t["asset"]: t for t in mobile["textures"]}
    effects = []
    for material in mobile["materials"]:
        if material["shader"] != "csgo_effects.vfx":
            continue
        texture = texture_by_path.get(material["base"])
        if texture and sha(root / "app/src/main/assets" / texture["asset"]) != texture["sha256"]:
            raise ValueError("mobile texture hash mismatch")
        effects.append({"materialId": material["id"], "source": material["source"], "shader": material["shader"],
                        "enabledInPreview": material["previewEnabled"], "baseTexture": texture,
                        "sourceTextureDependencies": material["allSourceParameters"]["texture_params"],
                        "reuseStatus": "AMBIENT_VISUAL_CANDIDATE_NOT_GRENADE_SMOKE_LOGIC"})
    write(output / "reusable-resources.json", {"schema": "c013-reusable-resource-audit-v1", "versions": versions,
          "ambientEffectMaterials": effects, "ambientEffectParts": 47, "ambientEffectTriangles": 852,
          "runtimeParticleDefinitionsAvailableInMobileBundle": False,
          "sourceSmokeFireHeRuntimeLogicAvailable": False,
          "characterPoseResource": None, "grenadeModelsResource": None,
          "characterAndGrenadeCoverageStatus": "NOT_DELIVERED_BY_CURRENT_MOBILE_BUNDLE; exact VPK dependency extraction remains separate",
          "warning": "Static dust/steam/lightshaft textures do not implement dynamic grenade effects"})
    print(f"Built {len(records)} partial records, {len(screenshots)} screenshots, {len(clips)} clip observations; formal import remains disabled")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    p.add_argument("--media-dir", type=Path, required=True)
    p.add_argument("--output", type=Path)
    args = p.parse_args()
    root = args.root.resolve()
    build(root, args.media_dir, args.output or root / "docs/calibration/C013")


if __name__ == "__main__":
    main()

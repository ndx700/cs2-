#!/usr/bin/env python3
"""Prepare the C014 three-lesson handoff from checked C013 evidence.

This is a resource handoff, not runtime data: all unmeasured positions,
trajectories, cameras and release-relative times remain null.
"""
import argparse
import hashlib
import json
from pathlib import Path

IDS = ("D2-001", "D2-010", "D2-014")
CAMERAS = ("stance", "aim", "overview", "follow", "landing")
APP_HEAD = "346c20fc5d36654b9ce7ca6bbfe956000780d428"
NOTES = {
    "D2-001": ("T出生中门烟", "匪家参考点，左键跳投。", "中门视线封锁"),
    "D2-010": ("A大Car火", "A大坑口桶上，按住W向前并左键跳投。", "车位及车后火区"),
    "D2-014": ("CT中门炸烟HE", "CT中路参考角落，瞄门框上方，左键普通投掷。", "中门烟内局部缺口"),
}
STAGES = {
    "D2-001": [("landing", "落地"), ("first_smoke", "起烟"),
               ("fully_formed", "扩散成型"), ("dissipation_begin", "开始消散"),
               ("dissipation_end", "完全消散")],
    "D2-010": [("impact", "落地破裂"), ("first_ignition", "初始着火"),
               ("late_region_ignition", "向车后蔓延"), ("final_coverage", "持续燃烧"),
               ("extinguish_end", "熄灭")],
    "D2-014": [("explosion", "雷爆炸"), ("hole_open", "局部冲烟缺口"),
               ("refill_begin", "开始回填"), ("refill_end", "完成回填")],
}


def load(path):
    return json.loads(path.read_text())


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def observation_draft(draft):
    """Expose the five APP camera slots without inventing orbit parameters."""
    draft = dict(draft)
    if draft["importable"] is not False or draft["status"] != "PENDING_CALIBRATION":
        raise ValueError("observation adapter requires a pending draft")
    draft["cameras"] = {name: None for name in CAMERAS}
    draft["observationContract"] = "docs/calibration/C014/app-observation-receipt.json"
    draft["missing"] = [*draft["missing"], "measured follow/landing orbit target, yaw/pitch/distance and obstruction check"]
    return draft


def validate_observation_draft(draft):
    if draft.get("coordinateSpace") != "display-m-y-up-v1" or draft.get("timeOrigin") != "throw-release-seconds":
        raise ValueError("APP coordinate/time convention mismatch")
    if draft.get("status") != "PENDING_CALIBRATION" or draft.get("importable") is not False:
        raise ValueError("observation draft cannot enable import")
    cameras = draft.get("cameras")
    if not isinstance(cameras, dict) or set(cameras) != set(CAMERAS):
        raise ValueError("C014 draft requires all five camera slots")
    if any(value is not None for value in cameras.values()):
        raise ValueError("unmeasured camera cannot use overview defaults")
    for key in ("foot", "eye", "aim", "bodyYaw", "aimFov", "throwHint", "begin", "aimAt", "throwAt", "duration", "path", "smoke", "fire", "he"):
        if draft.get(key) is not None or key not in draft:
            raise ValueError("unmeasured C014 runtime field: " + key)


def build(root):
    folder = root / "docs/calibration/C013"
    output = root / "docs/calibration/C014"
    output.mkdir(parents=True, exist_ok=True)
    records = {r["id"]: r for r in load(folder / "calibration-records.json")["records"]}
    images = load(folder / "screenshot-index.json")["screenshots"]
    clips = {r["courseId"]: r for r in load(folder / "clip-observations.json")["clips"]}
    queries = {r["id"]: r for r in load(folder / "coordinate-query-samples.json")["samples"]}
    adapter = load(folder / "app-adapter-report.json")
    receipt_path = output / "app-observation-receipt.json"
    receipt = load(receipt_path)
    if receipt["appHead"] != APP_HEAD or receipt["calibratedImportAllowed"] is not False or receipt["phoneVerified"] is not False:
        raise ValueError("observation receipt version/status mismatch")
    pending_output = output / "app-pending"
    pending_output.mkdir(exist_ok=True)
    version = digest(root / "app/src/main/assets/maps/dust2/mobile/manifest.json")
    video_path = output / "user-video-evidence.json"
    video_package = load(video_path) if video_path.exists() else None
    user_videos = {r["id"]: r for r in video_package["records"]} if video_package else {}
    lessons = []
    for course_id in IDS:
        r = records[course_id]
        title, hint, target = NOTES[course_id]
        draft_path = pending_output / (course_id + ".json")
        draft = observation_draft(load(folder / "app-pending" / (course_id + ".json")))
        user_video = user_videos.get(course_id)
        if user_video:
            title, hint, target = user_video["title"], user_video["hintZh"], user_video["targetZh"]
            draft["title"] = title
            draft["teachingSource"] = draft["effectSource"] = user_video["sourceUrl"]
            draft["evidence"]["url"] = user_video["sourceUrl"]
            draft["referenceSegment"] = "user-selected video; encoded segment timestamps in user-video-evidence.json"
            draft["referenceRevision"] = video_package["revision"]
            draft["aimTextureAcceptance"] = "docs/calibration/C014/aim-texture-checks.json#" + course_id
            draft["resourceObservations"] = {
                "sourceVideoEvidence": "docs/calibration/C014/user-video-evidence.json#" + course_id,
                "positionSemantics": "VIDEO_ONLY_NO_CERTIFIED_XYZ",
                "sourceConsolePosition": None, "displayConsolePosition": None,
                "sourceAnglesPitchYawRoll": None,
                "videoObservedThrowFlags": user_video["throwFlags"],
                "screenshotIds": [s["id"] for s in user_video["screenshots"]],
                "historicalC013ReferenceSuperseded": True,
            }
        validate_observation_draft(draft)
        draft_path.write_text(json.dumps(draft, ensure_ascii=False, indent=2) + "\n")
        lesson = {
            "id": course_id, "type": r["type"], "title": title,
            "status": "PENDING_CALIBRATION", "importable": False,
            "appDraft": {"path": str(draft_path.relative_to(root)), "sha256": digest(draft_path)},
            "mapVersion": version, "versions": r["versions"],
            "sourceDeclaredTeaching": {
                "url": r["teachingSource"], "hintZh": hint, "targetZh": target,
                "throwFlags": r["throwFlagsSourceDeclared"],
                "status": "SOURCE_DECLARED_NOT_GAME_VALIDATED",
                "matchToNartVariant": "UNCONFIRMED",
            },
            "screenshots": [i for i in images if i["courseId"] == course_id],
            "clipEvidence": clips[course_id],
            "unconfirmedConsolePosition": {
                "sourceXYZ": r["consolePositionSource"],
                "displayXYZ": r["consolePositionDisplay"],
                "semantics": r["consolePositionSemantics"],
                "worldQuery": queries.get(course_id),
                "usableAsFeetOrEye": False,
            },
            "measuredRuntime": {key: None for key in (
                "feet", "eye", "aim", "orientation", "throwRelease", "trajectory",
                "landingOrExplosion", "coverage", "followCamera", "landingCamera")},
            "timeOrigin": "own-grenade-release-seconds",
            "effectStages": [{"event": event, "labelZh": label,
                              "releaseRelativeSeconds": None, "measurementEvidence": None}
                             for event, label in STAGES[course_id]],
            "viewRequirements": {"main": "道具跟随，落地后观察效果",
                                 "rightWindow": "落点实时演示", "clock": "same-course-clock",
                                 "positionsCalibrated": False,
                                 "requiredCameras": list(CAMERAS),
                                 "orbitPositionMeans": "target, not camera eye",
                                 "followTarget": "eye before release; grenade in flight; final path point after landing",
                                 "landingTarget": "fixed independently measured effect target"},
            "missing": ["实机确认脚底/眼位/瞄点、朝向和FOV",
                        "带时间的轨迹、反弹点和落点/爆点",
                        "连续实机阶段时间、效果范围和结束过程",
                        "跟随及落点镜头位置、墙体遮挡检查",
                        "当前CS2地图/游戏版本、手机显示提示及隐藏提示对照"],
        }
        if course_id == "D2-001":
            lesson["missing"].append("参考站位图脚部在空中，尚不能证明落地站位")
        elif course_id == "D2-010":
            lesson["missing"].append("缺独立脚底接触图与数值站位；短片没有熄灭过程")
        else:
            lesson["missing"].append("缺独立站位图和连续成型烟→缺口→回填录像")
            lesson["smokeDependency"] = {
                "lessonId": "D2-001", "sceneId": "dust2-mid-doors-smoke",
                "requiredStateBeforeHERelease": "fully-formed",
                "smokeReleaseRelativeToHEReleaseSeconds": None,
                "smokeAgeAtHEReleaseSeconds": None,
                "clockNote": "HE释放为本课零点；预先成型的烟需已存在，不能随HE释放才从零龄起烟。未测偏移不填数值。",
                "parametersMustMatchSmokeLesson": True,
            }
        if user_video:
            lesson["referenceRevision"] = video_package["revision"]
            lesson["sourceDeclaredTeaching"] = {
                "url": user_video["sourceUrl"], "hintZh": hint, "targetZh": target,
                "throwFlags": user_video["throwFlags"],
                "status": "VIDEO_OBSERVED_NOT_GAME_REPRODUCED",
                "selectedVariant": user_video["selectedVariant"],
                "historicalNartAndGetReplaySuperseded": True,
            }
            lesson["screenshots"] = user_video["screenshots"]
            lesson["clipEvidence"] = {**user_video, "courseId": course_id}
            lesson["unconfirmedConsolePosition"] = {
                "sourceXYZ": None, "displayXYZ": None,
                "semantics": "NOT_PROVIDED_FOR_SELECTED_VIDEO_VARIANT",
                "worldQuery": None, "usableAsFeetOrEye": False,
            }
            lesson["missing"] = [*user_video["missing"],
                                 "游戏/地图版本与APP同视角/提示隐藏手机检查",
                                 "跟随/落点镜头三维参数及遮挡检查"]
            if course_id == "D2-014":
                lesson["smokeDependency"]["sceneId"] = "dust2-mid-doors-hang-smoke-user-v2"
                lesson["smokeDependency"]["sameVariantConfirmed"] = False
                lesson["smokeDependency"]["compatibilityEvidence"] = "视频含挂门烟炸开示范；与D2-001所选墙缝投法是否同一落点/形态仍待配套验证。"
        lessons.append(lesson)
    bundle = {
        "schema": "c014-resource-handoff-v1", "owner": "C014-RES", "batch": "M1",
        "mainBaseline": "aec0b1936f4de5053ba35100a30367e25915f505",
        "status": "READY_FOR_INTEGRATION", "formalLessonsCalibrated": False,
        "runtimeImportAllowed": False, "mapVersion": version,
        "appContract": adapter["appContract"],
        "appObservationReceipt": {"path": str(receipt_path.relative_to(root)),
                                  "sha256": digest(receipt_path), "appHead": APP_HEAD},
        "coordinateSpace": "dust2_display_y_up_v1",
        "lengthUnit": "display_unit", "sourceToDisplay": "[x*0.0254,z*0.0254,-y*0.0254]; once only",
        "appCoordinateAlias": "display-m-y-up-v1; identical values, no second conversion",
        "pageRecheckedAt": "2026-10-04", "lessons": lessons,
    }
    if video_package:
        bundle["activeReferenceRevision"] = video_package["revision"]
        bundle["userVideoEvidence"] = {"path": str(video_path.relative_to(root)), "sha256": digest(video_path)}
        aim_path = output / "aim-texture-checks.json"
        bundle["aimTextureAcceptance"] = {"path": str(aim_path.relative_to(root)), "sha256": digest(aim_path)}
    validate_bundle(bundle, root)
    (output / "three-lessons.json").write_text(json.dumps(bundle, ensure_ascii=False, indent=2) + "\n")
    print("PASS: C014 three-lesson evidence handoff; runtime import disabled")


def validate_bundle(bundle, root):
    if bundle.get("schema") != "c014-resource-handoff-v1" or bundle.get("runtimeImportAllowed") is not False or bundle.get("formalLessonsCalibrated") is not False:
        raise ValueError("unmeasured handoff cannot enable runtime import or calibration")
    lessons = bundle["lessons"]
    if [r["id"] for r in lessons] != list(IDS):
        raise ValueError("C014 stable identities/order changed")
    actual_map = digest(root / "app/src/main/assets/maps/dust2/mobile/manifest.json")
    video_info = bundle.get("userVideoEvidence")
    active_videos = {}
    if video_info:
        video_path = (root / video_info["path"]).resolve()
        if not video_path.is_relative_to(root.resolve()) or digest(video_path) != video_info["sha256"]:
            raise ValueError("user video evidence path/hash mismatch")
        video_package = load(video_path)
        if bundle.get("activeReferenceRevision") != video_package["revision"] or video_package["allCalibrated"] is not False or [r["id"] for r in video_package["records"]] != list(IDS):
            raise ValueError("user video revision/identity/status mismatch")
        active_videos = {r["id"]: r for r in video_package["records"]}
        aim_info = bundle["aimTextureAcceptance"]
        aim_path = (root / aim_info["path"]).resolve()
        if not aim_path.is_relative_to(root.resolve()) or digest(aim_path) != aim_info["sha256"]:
            raise ValueError("active aim evidence path/hash mismatch")
        aim = load(aim_path)
        if aim["referenceRevision"] != video_package["revision"] or aim["allCoursesCalibrated"] is not False or [r["id"] for r in aim["records"]] != list(IDS):
            raise ValueError("active aim revision/identity/status mismatch")
        for row in aim["records"]:
            if row["mapVersion"] != actual_map or any(v is not None for v in row["acceptance"].values()):
                raise ValueError("active aim map or pending acceptance mismatch")
            shots = {s["id"]: s for s in active_videos[row["id"]]["screenshots"]}
            if any(shot != shots.get(shot["id"]) for shot in row["referenceImages"]):
                raise ValueError("active aim reference must use selected video frames")
    receipt_info = bundle["appObservationReceipt"]
    receipt_path = (root / receipt_info["path"]).resolve()
    if not receipt_path.is_relative_to(root.resolve()) or digest(receipt_path) != receipt_info["sha256"]:
        raise ValueError("observation receipt path/hash mismatch")
    receipt = load(receipt_path)
    if receipt_info["appHead"] != APP_HEAD or receipt["appHead"] != APP_HEAD or receipt["calibratedImportAllowed"] is not False or receipt["phoneVerified"] is not False:
        raise ValueError("observation receipt version/status mismatch")
    if bundle["mapVersion"] != actual_map:
        raise ValueError("map version mismatch")
    for lesson in lessons:
        if lesson["mapVersion"] != actual_map or lesson["versions"]["mobileManifestSha256"] != actual_map:
            raise ValueError("lesson map version mismatch")
        if lesson["status"] != "PENDING_CALIBRATION" or lesson["importable"] is not False:
            raise ValueError("unmeasured lesson cannot be promoted")
        if lesson["type"] != {"D2-001": "SMOKE", "D2-010": "MOLOTOV", "D2-014": "HE"}[lesson["id"]]:
            raise ValueError("lesson type mismatch")
        if lesson["unconfirmedConsolePosition"]["usableAsFeetOrEye"] is not False or any(x is not None for x in lesson["measuredRuntime"].values()):
            raise ValueError("source command cannot become runtime coordinates")
        if any(s["releaseRelativeSeconds"] is not None or s["measurementEvidence"] is not None for s in lesson["effectStages"]):
            raise ValueError("unmeasured release-relative timing")
        for item in [lesson["appDraft"], *lesson["screenshots"],
                     {"path": lesson["clipEvidence"]["observationStrip"], "sha256": lesson["clipEvidence"]["observationStripSha256"]}]:
            p = (root / item["path"]).resolve()
            if not p.is_relative_to(root.resolve()) or digest(p) != item["sha256"]:
                raise ValueError("evidence path/hash mismatch")
        draft = load(root / lesson["appDraft"]["path"])
        validate_observation_draft(draft)
        if active_videos:
            selected = active_videos[lesson["id"]]
            if lesson.get("referenceRevision") != bundle["activeReferenceRevision"] or lesson["sourceDeclaredTeaching"]["url"] != selected["sourceUrl"] or lesson["sourceDeclaredTeaching"]["selectedVariant"] != selected["selectedVariant"]:
                raise ValueError("active video variant/source mismatch")
            if any(lesson["unconfirmedConsolePosition"][key] is not None for key in ("sourceXYZ", "displayXYZ", "worldQuery")):
                raise ValueError("superseded command cannot cross into selected video variant")
            observations = draft["resourceObservations"]
            if draft.get("referenceRevision") != bundle["activeReferenceRevision"] or draft["teachingSource"] != selected["sourceUrl"] or any(observations.get(k) is not None for k in ("sourceConsolePosition", "displayConsolePosition", "sourceAnglesPitchYawRoll")):
                raise ValueError("APP draft still uses superseded reference")
            if draft["aimTextureAcceptance"] != bundle["aimTextureAcceptance"]["path"] + "#" + lesson["id"]:
                raise ValueError("APP draft uses superseded aiming evidence")
            if lesson["sourceDeclaredTeaching"]["throwFlags"] != selected["throwFlags"]:
                raise ValueError("selected video throw flags mismatch")
        if draft["lessonId"] != lesson["id"] or draft["mapVersion"] != actual_map or draft["importable"] is not False:
            raise ValueError("APP draft identity/version/import mismatch")
        if any(image["courseId"] != lesson["id"] for image in lesson["screenshots"]) or lesson["clipEvidence"]["courseId"] != lesson["id"]:
            raise ValueError("evidence belongs to a different course")
    he = lessons[2]["smokeDependency"]
    if he["lessonId"] != lessons[0]["id"] or he["requiredStateBeforeHERelease"] != "fully-formed" or he["parametersMustMatchSmokeLesson"] is not True:
        raise ValueError("HE must reference the same already-formed smoke")
    if he["smokeReleaseRelativeToHEReleaseSeconds"] is not None or he["smokeAgeAtHEReleaseSeconds"] is not None:
        raise ValueError("unmeasured HE/smoke clock offset")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    build(parser.parse_args().root.resolve())

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


def build(root):
    folder = root / "docs/calibration/C013"
    output = root / "docs/calibration/C014"
    output.mkdir(parents=True, exist_ok=True)
    records = {r["id"]: r for r in load(folder / "calibration-records.json")["records"]}
    images = load(folder / "screenshot-index.json")["screenshots"]
    clips = {r["courseId"]: r for r in load(folder / "clip-observations.json")["clips"]}
    queries = {r["id"]: r for r in load(folder / "coordinate-query-samples.json")["samples"]}
    adapter = load(folder / "app-adapter-report.json")
    version = digest(root / "app/src/main/assets/maps/dust2/mobile/manifest.json")
    lessons = []
    for course_id in IDS:
        r = records[course_id]
        title, hint, target = NOTES[course_id]
        draft_path = folder / "app-pending" / (course_id + ".json")
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
                                 "positionsCalibrated": False},
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
        lessons.append(lesson)
    bundle = {
        "schema": "c014-resource-handoff-v1", "owner": "C014-RES", "batch": "M1",
        "mainBaseline": "aec0b1936f4de5053ba35100a30367e25915f505",
        "status": "READY_FOR_INTEGRATION", "formalLessonsCalibrated": False,
        "runtimeImportAllowed": False, "mapVersion": version,
        "appContract": adapter["appContract"],
        "coordinateSpace": "dust2_display_y_up_v1",
        "lengthUnit": "display_unit", "sourceToDisplay": "[x*0.0254,z*0.0254,-y*0.0254]; once only",
        "appCoordinateAlias": "display-m-y-up-v1; identical values, no second conversion",
        "pageRecheckedAt": "2026-10-04", "lessons": lessons,
    }
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

#!/usr/bin/env python3
"""Index user-selected recordings and extract review frames, never runtime values.

Requires ffmpeg/ffprobe; raw videos stay outside the repository. An encoded
video timestamp is not certified game time and never enables course import.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw

RECIPES = {
    "D2-001": {
        "name": "41124105311-1-192.mp4", "title": "中门挂门烟",
        "url": "https://b23.tv/efWPjTd", "libraryId": "libfile_91eb381795d4819199aa9b53bf62f3b7",
        "expectedSha256": "53897447f28b2c2f554df97500cd8d670d1e46d2cfffc1beeb36daa53b067a62",
        "variant": "hang-door-first-lineup-no-forward-adjustment",
        "hintZh": "在矮墙旁蹲下对齐墙缝与墙边，站起后瞄墙面小污点，左键跳投；不要加入后段向前移动的满封烟站位。",
        "targetZh": "中门挂门烟", "throwFlags": {"jump": True, "forward": False, "crouchAtRelease": False, "left_click": True},
        "frames": [(12, "stance-seam"), (18, "stance-wall-edge"), (20, "stand-up"), (21, "aim-mark"),
                   (22.9, "release-before"), (23.2, "release-after"), (27, "landing-view"),
                   (30, "excluded-forward-adjustment"), (40, "excluded-full-seal")],
        "segments": [{"start": 11, "end": 27.5, "role": "selected-hang-door-recipe"},
                     {"start": 28, "end": 44, "role": "excluded-full-seal-variant"}],
        "releaseInterval": [22.9, 23.2],
        "observation": "前段演示墙缝/墙边对齐、站起、污点瞄准及左键跳投；后段向前调整产生满封烟，必须独立保存。录像存在运镜/剪切和明显HUD时钟跳变，没有烟完整消散过程。",
        "missing": ["精确脚底/眼位/朝向/FOV及瞄点坐标", "第一种挂门烟的完整连续效果与结束过程", "轨迹/落点/门洞覆盖的三维标定"],
    },
    "D2-010": {
        "name": "沙二蓝车满烧火.mp4", "title": "蓝车满烧火",
        "url": "https://b23.tv/NwO4dLO", "libraryId": "libfile_006bbb82134c8191a3eec00f302eba82",
        "expectedSha256": "6ef813b55497257fbd22769c3371b7f4a9d9923935ab0796112f4937da9dc5bc",
        "variant": "barrel-wall-door-lock-w-jumpthrow",
        "hintZh": "跳上蓝色油桶并抵住墙，瞄门锁最上沿，W向前跳投。",
        "targetZh": "蓝车及车后地面火区", "throwFlags": {"jump": True, "forward": True, "crouchAtRelease": False, "left_click": None},
        "frames": [(11, "stance-barrel"), (13, "stance-wall"), (18, "aim-door-lock"),
                   (21.9, "release-before"), (22.1, "release-after"), (24, "flight-reference"),
                   (25, "first-visible-fire"), (26, "initial-coverage"), (29, "later-coverage")],
        "segments": [{"start": 0, "end": 7, "role": "intro-effect-separate-trial"},
                     {"start": 9, "end": 29.7, "role": "teaching-and-effect-with-moving-camera"}],
        "releaseInterval": [21.9, 22.1],
        "observation": "字幕明确桶上靠墙、门锁上沿和W跳投；25～29秒取样可见蓝车火区扩大。手部动画符合普通持瓶投掷，但画面无按键记录，因此鼠标按钮不认证。录像止于燃烧阶段，未展示熄灭。",
        "missing": ["桶顶脚底/眼位/角度和门锁瞄点三维坐标", "鼠标按钮实机确认", "轨迹反弹/落点及地面覆盖标定", "完整熄灭和游戏时钟速率"],
    },
    "D2-014": {
        "name": "刷到的永远更好用！2000h道具手自研无敌中门炸烟雷.mp4", "title": "中门炸烟雷",
        "url": "https://b23.tv/IwqgfkC", "libraryId": "libfile_d6c7eaeca9b0819197c7312cce120c70",
        "expectedSha256": "276cab1c44837e67ee034b874d3e6b7ee23f8df73aa5c31fbec10ef8d698e809",
        "variant": "truck-sand-corner-crouch-jumpthrow",
        "hintZh": "卡住沙堆与蓝卡车车头角落，蹲下瞄左门右下角，保持蹲姿按空格加左键；不可站起再跳投。",
        "targetZh": "中门烟内局部冲烟区", "throwFlags": {"jump": True, "forward": False, "crouchAtRelease": True, "left_click": True},
        "frames": [(5, "stance-sand-truck-corner"), (9, "aim-left-door-bottom-right"),
                   (10.5, "release-before"), (10.7, "release-after"), (17, "excluded-stand-up-failure"),
                   (22, "crouch-instruction"), (32, "hang-smoke-present"), (35, "hang-smoke-before-he"),
                   (37, "he-flight-at-hang-smoke"), (38, "clear-doorway-after-cut")],
        "segments": [{"start": 3, "end": 14.5, "role": "selected-crouch-jumpthrow"},
                     {"start": 15, "end": 20.5, "role": "excluded-stand-up-before-jump-failure"},
                     {"start": 21, "end": 27.5, "role": "crouch-correction-and-throw"},
                     {"start": 31.5, "end": 38.5, "role": "hang-smoke-interaction-with-camera-cut"},
                     {"start": 39, "end": 86, "role": "other-smokes-and-alternative-aim"}],
        "releaseInterval": [10.5, 10.7],
        "observation": "画面/字幕确认沙堆卡车角落蹲姿瞄点，保持蹲姿空格加左键；站起再跳是失败示范。32～37秒显示挂门烟及HE飞行，38秒门洞打开，但37～38秒HUD时钟从55:00跳到54:47，不能测真实炸开延迟或用后续别颗烟作为回填。",
        "missing": ["卡车/沙堆脚底眼位、瞄点和爆点三维坐标", "与新D2-001同投法同落点的配套实机确认", "无剪切的缺口形成至回填及范围", "烟预置年龄和HE释放相对偏移"],
    },
}


def digest(path):
    with path.open("rb") as stream:
        checksum = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
        return checksum.hexdigest()


def extract(root, input_dir):
    folder = root / "docs/calibration/C014"
    shots = folder / "user-video-frames"
    shots.mkdir(parents=True, exist_ok=True)
    rows = []
    for course_id, spec in RECIPES.items():
        video = input_dir / spec["name"]
        if digest(video) != spec["expectedSha256"]:
            raise ValueError("source video changed: " + spec["name"])
        probe = json.loads(subprocess.check_output([
            "ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(video)]))
        track = next(s for s in probe["streams"] if s["codec_type"] == "video")
        frames = []
        for time, role in spec["frames"]:
            name = f"{course_id}-{time:05.1f}-{role}.jpg"
            image = shots / name
            subprocess.run(["ffmpeg", "-v", "error", "-ss", str(time), "-i", str(video),
                            "-frames:v", "1", "-q:v", "3", "-y", str(image)], check=True)
            frames.append({"id": f"{course_id}-user-{time:05.1f}", "courseId": course_id,
                           "path": str(image.relative_to(root)), "role": role,
                           "sourceVideoSeconds": time, "sha256": digest(image),
                           "size": [track["width"], track["height"]]})
        sheet = Image.new("RGB", (1280, ((len(frames) + 2) // 3) * 264), "#18202a")
        draw = ImageDraw.Draw(sheet)
        for index, frame in enumerate(frames):
            with Image.open(root / frame["path"]) as image:
                image.thumbnail((420, 236))
                x, y = (index % 3) * 426, (index // 3) * 264
                sheet.paste(image, (x, y))
                draw.text((x + 8, y + 239), f"{course_id} {frame['sourceVideoSeconds']:.1f}s", fill="white")
        strip = shots / (course_id + "-review.jpg")
        sheet.save(strip, quality=90)
        rows.append({
            "id": course_id, "title": spec["title"], "sourceName": spec["name"],
            "sourceUrl": spec["url"], "libraryId": spec["libraryId"],
            "sourceSha256": spec["expectedSha256"], "bytes": video.stat().st_size,
            "encodedDurationSeconds": float(probe["format"]["duration"]),
            "video": {key: track[key] for key in ("width", "height", "r_frame_rate", "codec_name")},
            "clock": "ENCODED_VIDEO_SECONDS_NOT_CERTIFIED_GAME_TIME",
            "selectedVariant": spec["variant"], "hintZh": spec["hintZh"], "targetZh": spec["targetZh"],
            "throwFlags": {**spec["throwFlags"], "status": "VIDEO_OBSERVED_NOT_GAME_REPRODUCED"},
            "segments": spec["segments"], "screenshots": frames,
            "observationStrip": str(strip.relative_to(root)), "observationStripSha256": digest(strip),
            "releaseAnimationVideoIntervalSeconds": spec["releaseInterval"],
            "releaseGameTimeSeconds": None, "sourceGameBuild": None, "sourceMapBuild": None,
            "fullLifecycleObserved": False, "formalImportAllowed": False,
            "observation": spec["observation"], "missing": spec["missing"],
            "videoNotBundledInAPK": True,
        })
    result = {"schema": "c014-user-video-evidence-v1", "revision": "user-selected-20261004-v2",
              "stableIds": list(RECIPES), "allCalibrated": False,
              "selectionAuthority": "User replaced old mid smoke with hang-door smoke and provided these three recordings.",
              "records": rows}
    (folder / "user-video-evidence.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(f"Indexed {len(rows)} user recordings / {sum(len(r['screenshots']) for r in rows)} review frames; no runtime promotion")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--input-dir", type=Path, required=True)
    args = parser.parse_args()
    extract(args.root.resolve(), args.input_dir.resolve())

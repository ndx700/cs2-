#!/usr/bin/env python3
"""Read C012's four priority pages; retain evidence separately from calibration.

The default writes URLs, source commands and hashes, not calibrated CS2 values.
--media-dir downloads bounded source media for inspection outside the app bundle.
"""
import argparse
import hashlib
import html
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

PRIORITY = ("D2-001", "D2-014", "D2-009", "D2-010")


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.sections = []
        self.media = []
        self.text = []
        self.ignored = 0

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "section":
            self.sections.append(a.get("aria-labelledby"))
        if tag in ("script", "style"):
            self.ignored += 1
        if tag in ("img", "video") and a.get("src"):
            url = a["src"]
            if urllib.parse.urlparse(url).hostname == "storage.getreplay.gg":
                self.media.append({"url": url, "kind": tag,
                                   "section": self.sections[-1] if self.sections else None,
                                   "label": a.get("alt"),
                                   "provenance": "WEBPAGE_MEDIA_NOT_RUNTIME_LOGIC"})

    def handle_endtag(self, tag):
        if tag == "section" and self.sections:
            self.sections.pop()
        if tag in ("script", "style"):
            self.ignored = max(0, self.ignored - 1)

    def handle_data(self, data):
        if not self.ignored and data.strip():
            self.text.append(data.strip())


def read_url(url, limit):
    request = urllib.request.Request(url, headers={"User-Agent": "C013-resource-reference-audit/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        body = response.read(limit + 1)
        if len(body) > limit:
            raise ValueError("media exceeds bounded download limit")
        return body, response.geturl(), response.headers.get("Content-Type"), response.headers.get("Last-Modified")


def extract_commands(text):
    number = r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)"
    pattern = r"setpos\s+(%s)\s+(%s)\s+(%s);\s*setang\s+(%s)\s+(%s)\s+(%s)" % ((number,) * 6)
    found = []
    for m in re.finditer(pattern, html.unescape(text)):
        item = {"position_source_xyz": [float(x) for x in m.groups()[:3]],
                "angles_source_pitch_yaw_roll_degrees": [float(x) for x in m.groups()[3:]],
                "command": m.group(), "status": "SOURCE_DECLARED_NOT_GAME_VALIDATED"}
        if item not in found:
            found.append(item)
    return found


def collect(candidates, output, media_dir=None):
    output.mkdir(parents=True, exist_ok=True)
    records = []
    by_id = {entry["id"]: entry for entry in candidates["entries"]}
    for identity in PRIORITY:
        entry = by_id[identity]
        record = {"id": identity, "teaching_source_url": entry["teaching_source_url"],
                  "effect_source_url_in_C012": entry["effect_source_url"],
                  "reference_segment_in_C012": entry["reference_segment_in_source"],
                  "retrieved_at": datetime.now(timezone.utc).isoformat(),
                  "map_build": None, "game_build": None, "calibration_status": "PENDING_CALIBRATION"}
        try:
            data, final, mime, modified = read_url(entry["teaching_source_url"], 4 * 1024 * 1024)
            text = data.decode("utf-8")
            page = Page()
            page.feed(text)
            title = re.search(r"<title>(.*?)</title>", text, re.S)
            record.update({"status": "PAGE_FETCHED", "final_url": final, "content_type": mime,
                           "page_last_modified": modified, "html_bytes": len(data),
                           "html_sha256": hashlib.sha256(data).hexdigest(),
                           "title": html.unescape(title.group(1)) if title else None,
                           "source_commands": extract_commands(text), "media": page.media})
            visible = " ".join(page.text)
            # A short factual source observation, not a copy of the full page.
            hint = re.search(r"Hint (.*?) (?:Practice from exact position|Where it lands|Damage zone|Revealed area|Blocked area|How to throw)", visible)
            hint_text = hint.group(1).lower() if hint else ""
            record["throw_flags_from_page_hint"] = {
                "jump": "jump" in hint_text, "forward": "hold w" in hint_text,
                "standing": "standing" in hint_text or "plain left" in hint_text,
                "left_click": "left click" in hint_text or "left-click" in hint_text,
                "status": "SOURCE_DECLARED_NOT_GAME_VALIDATED"}
            if media_dir:
                for index, item in enumerate(record["media"]):
                    suffix = Path(urllib.parse.urlparse(item["url"]).path).suffix
                    dest = media_dir / identity / f"{index:02d}{suffix}"
                    try:
                        body, final, mime, modified = read_url(item["url"], 64 * 1024 * 1024)
                        dest.parent.mkdir(parents=True, exist_ok=True)
                        dest.write_bytes(body)
                        item.update({"download_status": "FETCHED", "inspection_file": str(Path(identity) / dest.name),
                                     "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest(),
                                     "content_type": mime, "last_modified": modified})
                    except (OSError, ValueError) as error:
                        item.update({"download_status": "FAILED", "error": str(error)})
        except (OSError, ValueError, UnicodeError) as error:
            record.update({"status": "FETCH_FAILED", "error": str(error)})
        records.append(record)
        (output / "source-pages.json").write_text(json.dumps({"schema": "c013-source-evidence-v1", "records": records}, ensure_ascii=False, indent=2) + "\n")
        print(identity, record["status"], "commands", len(record.get("source_commands", [])), "media", len(record.get("media", [])), flush=True)
    return records


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidates", type=Path, default=Path(__file__).resolve().parents[1] / "docs/materials/dust2-v1-candidates.json")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--media-dir", type=Path)
    args = parser.parse_args()
    records = collect(json.loads(args.candidates.read_text()), args.output, args.media_dir)
    if any(item["status"] != "PAGE_FETCHED" for item in records):
        raise SystemExit(1)


if __name__ == "__main__":
    main()

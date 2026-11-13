"""Pull a bounded, evenly spaced sample of photos from the attached phone into raw/photos/ (gitignored).

Uses MediaStore via `adb shell content query`; only JPEG/PNG/WebP (what PIL opens without extras).
Writes raw/photos/manifest.json: [{"id", "name", "file", "mime"}]. Names are the maintainer's private
filenames: keep raw/ and results/ out of git.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADB = os.environ.get("ADB") or str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe")
SERIAL = os.environ.get("ANDROID_SERIAL", "RFCT703FBKR")
LIMIT = int(sys.argv[1]) if len(sys.argv) > 1 else 300
OK_MIME = {"image/jpeg", "image/png", "image/webp"}


def adb(*args, **kw):
    return subprocess.run([ADB, "-s", SERIAL, *args], check=True, capture_output=True, **kw)


def query_images():
    out = adb("shell", "content", "query", "--uri", "content://media/external/images/media",
              "--projection", "_id:_display_name:mime_type:_data:_size").stdout.decode("utf-8", "replace")
    rows = []
    for line in out.splitlines():
        m = re.match(r"Row: \d+ _id=(\d+), _display_name=(.*), mime_type=(.*), _data=(.*), _size=(\d*)$", line)
        if m:
            rows.append({"id": int(m[1]), "name": m[2], "mime": m[3], "path": m[4], "size": int(m[5] or 0)})
    return sorted(rows, key=lambda r: r["id"])


def main():
    rows = query_images()
    usable = [r for r in rows if r["mime"] in OK_MIME and r["size"] > 0]
    print(f"images in MediaStore: {len(rows)}; jpeg/png/webp non-empty: {len(usable)}")
    if len(usable) > LIMIT:
        step = len(usable) / LIMIT
        usable = [usable[int(i * step)] for i in range(LIMIT)]
    target = ROOT / "raw" / "photos"
    target.mkdir(parents=True, exist_ok=True)
    manifest = []
    for r in usable:
        ext = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}[r["mime"]]
        local = target / f"{r['id']}.{ext}"
        if not local.exists():
            adb("pull", r["path"], str(local))
        manifest.append({"id": r["id"], "name": r["name"], "file": local.name, "mime": r["mime"]})
    (target / "manifest.json").write_text(json.dumps(manifest, indent=1), encoding="utf-8")
    print(f"pulled {len(manifest)} photos into {target}")


if __name__ == "__main__":
    main()

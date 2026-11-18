"""Serve private/ on 127.0.0.1 only and accept the labelling page's saves.

usage (from tools/face-model-spike): venv/Scripts/python scripts/serve_labels.py
then open http://127.0.0.1:8765/label/index.html

POST /save writes the request body to private/labels/labels.json (fixed path, 5 MB cap, must parse as JSON); nothing else is writable.
"""
import json
from functools import partial
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

PRIVATE = Path(__file__).resolve().parent.parent / "private"
TARGET = PRIVATE / "labels" / "labels.json"
MAX_BYTES = 5_000_000


class Handler(SimpleHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        if self.path != "/save" or length <= 0 or length > MAX_BYTES:
            self.send_error(400)
            return
        body = self.rfile.read(length)
        try:
            json.loads(body)
        except ValueError:
            self.send_error(400, "not JSON")
            return
        TARGET.parent.mkdir(parents=True, exist_ok=True)
        if TARGET.exists():
            TARGET.replace(TARGET.with_suffix(".prev.json"))
        TARGET.write_bytes(body)
        self.send_response(204)
        self.end_headers()


if __name__ == "__main__":
    print(f"serving {PRIVATE} at http://127.0.0.1:8765/label/index.html  (saves to {TARGET})")
    HTTPServer(("127.0.0.1", 8765), partial(Handler, directory=str(PRIVATE))).serve_forever()

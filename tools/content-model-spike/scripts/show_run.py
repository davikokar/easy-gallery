"""Print the scalar fields and list sizes of a device result json.  usage: show_run.py <tag>"""
import json
import sys

import evaluate as ev

d = json.loads((ev.RES / "device" / f"{sys.argv[1]}.json").read_text())


def show(d, p=""):
    for k, v in d.items():
        if isinstance(v, dict):
            show(v, p + k + ".")
        elif isinstance(v, list):
            print(p + k, "list", len(v), v[:2] if v and not isinstance(v[0], (dict, list)) else "")
        else:
            print(p + k, v)


show(d)

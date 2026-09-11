# -*- coding: utf-8 -*-
import re
import os
from collections import OrderedDict

root = r"app/src/main/java"
pat = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')
ru = re.compile(r"[А-Яа-яЁё]")
found = OrderedDict()

for dirpath, _, files in os.walk(root):
    for f in files:
        if not f.endswith(".kt"):
            continue
        path = os.path.join(dirpath, f)
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        for m in pat.finditer(text):
            s = m.group(1)
            if ru.search(s):
                found[s] = found.get(s, 0) + 1

print(f"unique={len(found)} total_occ={sum(found.values())}")
for s, c in sorted(found.items(), key=lambda x: -x[1]):
    print(f"{c:3} | {s}")

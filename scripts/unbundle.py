#!/usr/bin/env python3
"""Recreate the FocusLock project tree from FocusLock-all-source.txt.
Usage: python3 unbundle.py FocusLock-all-source.txt [output_dir]   (default output_dir: FocusLock)"""
import base64, os, sys

src = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else "FocusLock"
text = open(src, encoding="utf-8", newline="").read()
parts = text.split("\n=== FILE: ")
count = 0
for part in parts[1:]:
    header, _, body = part.partition(" ===\n")
    binary = header.endswith(" [base64]")
    path = header[:-9] if binary else header
    if path.startswith("/") or ".." in path.split("/"):
        sys.exit("refusing unsafe path: " + path)
    dest = os.path.join(out, path)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    body = body[:-1] if body.endswith("\n") else body
    if binary:
        open(dest, "wb").write(base64.b64decode(body))
    else:
        open(dest, "w", encoding="utf-8", newline="\n").write(body + "\n")
    count += 1
if os.path.exists(os.path.join(out, "gradlew")):
    os.chmod(os.path.join(out, "gradlew"), 0o755)
    os.chmod(os.path.join(out, "scripts/verify-domain.sh"), 0o755)
print(f"restored {count} files into {out}/")

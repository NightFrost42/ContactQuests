#!/usr/bin/env python3
"""Select only the production mod jar and verify its expanded metadata."""
import hashlib
import json
from pathlib import Path
import shutil
import tomllib
import zipfile


release = json.loads(Path("release-dist/release.json").read_text(encoding="utf-8"))
expected = Path("build/libs") / ("contactquests-" + release["version"] + ".jar")
if not expected.is_file():
    raise SystemExit(f"Production mod jar is missing: {expected}")
with zipfile.ZipFile(expected) as jar:
    metadata = tomllib.loads(jar.read("META-INF/mods.toml").decode("utf-8"))
    mods = metadata["mods"]
    assert len(mods) == 1 and mods[0]["modId"] == "contactquests", "Wrong mod jar"
    assert mods[0]["version"] == release["version"], "Jar version does not match the Release"
    assert not any(p.startswith("com/creeperyang/contactquests/test/") for p in jar.namelist()), "Test mod was packaged"
    assert "${" not in str(metadata), "Unexpanded jar metadata"
shutil.copy2(expected, "release-dist/" + expected.name)
digest = hashlib.sha256(expected.read_bytes()).hexdigest()
Path("release-dist/SHA256SUMS").write_text(digest + "  " + expected.name + "\n", encoding="utf-8")
print(f"Verified {expected.name}: {digest}")

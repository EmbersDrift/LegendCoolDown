"""Validate distribution boundaries and save repeatable local verification evidence."""
from pathlib import Path
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = next(line.split("=", 1)[1].strip() for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("mod_version="))
report = {"mod": "legendcooldown", "version": VERSION, "versions": []}
mixins = {"CooldownActionBarMixin", "BossHealthOverlayAccessor", "ItemCooldownsAccessor", "ItemCooldownsMixin"}
for project, version, java in [("mc12111", "1.21.11", 21), ("mc2612", "26.1.2", 25), ("mc262", "26.2", 25)]:
    jar = ROOT / "dist" / f"LegendCoolDown-{VERSION}+mc{version}.jar"
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        assert metadata["id"] == "legendcooldown"
        assert set(metadata["depends"]) == {"minecraft", "java", "fabricloader", "fabric-api"}
        assert metadata["depends"]["minecraft"] == version
        assert metadata["depends"]["java"] == f">={java}"
        assert set(json.loads(archive.read("legendcooldown.mixins.json"))["client"]) == mixins
        for name in archive.namelist():
            assert "SmokeTest" not in name and "legendcooldown_smoke" not in name
            if name.endswith(".class"):
                assert int.from_bytes(archive.read(name)[6:8], "big") == java + 44
    suites = [ET.parse(path).getroot() for path in (ROOT / project / "build/test-results/test").glob("TEST-*.xml")]
    tests = {key: sum(int(s.attrib[key]) for s in suites) for key in ["tests", "failures", "errors"]}
    assert tests["tests"] == 25 and tests["failures"] == tests["errors"] == 0
    run = ROOT / project / "build/smoke-run"
    assert not (run / "smoke-failure.txt").exists()
    checks = (run / "smoke-result.txt").read_text(encoding="utf-8").splitlines()
    assert len(checks) == 37
    pictures = ROOT / "docs/verification" / version
    pictures.mkdir(parents=True, exist_ok=True)
    for name in ["settings", "diagnostics", "hud-ring", "hud-icon", "hud-bar", "hud-text", "hud-editor"]:
        shutil.copy2(run / "screenshots" / f"{name}.png", pictures / f"{name}.png")
    report["versions"].append({
        "minecraft": version, "javaBytecode": java, "artifact": str(jar.relative_to(ROOT)),
        "bytes": jar.stat().st_size, "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "unitTests": tests, "clientSmokeChecks": checks,
        "screenshots": str(pictures.relative_to(ROOT)),
    })
target = ROOT / "docs/verification/results.json"
target.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("Verified three release JARs, 75 unit tests and 111 client smoke checks.")

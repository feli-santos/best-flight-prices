#!/usr/bin/env python3
"""Docs checks: required files, relative links, ADR index, trailing whitespace."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = [
    "README.md", "AGENTS.md", "LICENSE", "CHANGELOG.md", "CONTRIBUTING.md", "SECURITY.md",
    *[f"docs/{n}.md" for n in (
        "PRODUCT ARCHITECTURE PARITY API SEARCH_AND_SCORING DATA_SOURCES TESTING LOCAL_LAB "
        "LOCALIZATION DEVELOPMENT BACKEND_DEPLOY DEVICE_INSTALL PRIVACY ROADMAP GLOSSARY").split()],
    "docs/adr/README.md",
]
LINK = re.compile(r"\]\(([^)#\s]+)(?:#[^)]*)?\)")


def check(root: Path = ROOT) -> list[str]:
    errors = []
    for rel in REQUIRED:
        if not (root / rel).exists():
            errors.append(f"missing required file: {rel}")
    md_files = [p for p in root.rglob("*.md") if ".git" not in p.parts and "build" not in p.parts]
    for md in md_files:
        text = md.read_text(encoding="utf-8")
        for n, line in enumerate(text.splitlines(), 1):
            if line != line.rstrip():
                errors.append(f"{md.relative_to(root)}:{n}: trailing whitespace")
        for target in LINK.findall(text):
            if re.match(r"^[a-z]+:", target):
                continue
            if not (md.parent / target).exists():
                errors.append(f"{md.relative_to(root)}: broken link {target}")
    adr_dir = root / "docs/adr"
    if adr_dir.exists():
        index = (adr_dir / "README.md").read_text(encoding="utf-8")
        for adr in sorted(adr_dir.glob("[0-9][0-9][0-9][0-9]-*.md")):
            if adr.name not in index:
                errors.append(f"ADR not in index: {adr.name}")
    return errors


if __name__ == "__main__":
    errs = check()
    print("\n".join(errs) if errs else "docs-check: ok")
    sys.exit(1 if errs else 0)

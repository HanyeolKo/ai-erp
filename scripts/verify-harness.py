#!/usr/bin/env python3
"""Verify the project harness, Codex projections, routing, and pinned skill bytes."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tomllib

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
FACTORY = ROOT / "scripts/vendor/harness-factory"


def checked_path(root: Path, relative: str) -> Path:
    path = (root / relative).resolve()
    if Path(relative).is_absolute() or not path.is_relative_to(root.resolve()):
        raise ValueError(f"path escapes source root: {relative}")
    return path


def check_digest(path: Path, expected: str, errors: list[str]) -> None:
    if not path.is_file():
        errors.append(f"missing locked file: {path}")
    elif hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        errors.append(f"SHA-256 mismatch: {path}")


def load_factory():
    source = json.loads((FACTORY / "SOURCE.json").read_text(encoding="utf-8"))
    errors: list[str] = []
    for relative, digest in source["files"].items():
        check_digest(checked_path(FACTORY, relative), digest, errors)
    if errors:
        raise ValueError("\n".join(errors))
    spec = importlib.util.spec_from_file_location(
        "harness_factory_validator", FACTORY / "scripts/validate_runtime_neutral.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


factory = load_factory()


class ProjectValidator(factory.Validator):
    def check_keys(self, value, label, required, allowed):
        # Factory 0.3.0 predates model inheritance in standalone Codex agents.
        # https://learn.chatgpt.com/docs/agent-configuration/subagents
        # All other schema, permission, path and parity checks stay unchanged.
        if re.fullmatch(r"Codex agent [a-z0-9-]+\.toml", label):
            required = required - {"model", "model_reasoning_effort"}
        super().check_keys(value, label, required, allowed)


def verify_vendor(root: Path) -> list[str]:
    errors: list[str] = []
    vendor_root = root / "vendor/ux-skills"
    lock = json.loads((vendor_root / "skills.lock.json").read_text(encoding="utf-8"))
    expected_ids = {"frontend-design", "ui-ux-pro-max", "web-design-guidelines", "web-interface-guidelines-reference"}
    sources = lock["sources"]
    if {source["id"] for source in sources} != expected_ids or len(sources) != 4:
        errors.append("vendor lock must contain the three UI/UX skills and the Vercel reference")
    locked: set[Path] = set()
    for source in sources:
        if not re.fullmatch(r"[0-9a-f]{40}", source["commit"]):
            errors.append(f"vendor source needs an immutable commit: {source['id']}")
        if not source["files"]:
            errors.append(f"vendor source has no files: {source['id']}")
        for relative, metadata in source["files"].items():
            path = checked_path(root, relative)
            if not path.is_relative_to(vendor_root.resolve()):
                errors.append(f"locked file outside vendor/ux-skills: {relative}")
                continue
            if path in locked:
                errors.append(f"duplicate locked file: {relative}")
            locked.add(path)
            check_digest(path, metadata["sha256"], errors)
            if path.is_file() and path.stat().st_size != metadata["bytes"]:
                errors.append(f"locked file size mismatch: {relative}")
    local_metadata = {vendor_root / "skills.lock.json", vendor_root / "THIRD-PARTY.md"}
    for path in vendor_root.rglob("*"):
        if path.is_file() and path.resolve() not in locked and path not in local_metadata:
            if "__pycache__" not in path.parts and path.suffix != ".pyc":
                errors.append(f"unlocked vendor file: {path.relative_to(root)}")
    return errors


def verify_routing(root: Path, spec: dict) -> list[str]:
    errors: list[str] = []
    edges = {(item["from"], item["to"]) for item in spec["orchestration"]["handoffs"]}
    required = {("router", "ui-ux-designer"), ("ui-ux-designer", "reviewer")}
    if not required.issubset(edges) or ("router", "reviewer") in edges:
        errors.append("UI routing must pass from router through ui-ux-designer to reviewer")
    for skill in spec["skills"]:
        if skill["kind"] == "domain" and skill["entry_agent"] != "ui-ux-designer":
            errors.append(f"UI skill must enter ui-ux-designer: {skill['id']}")
    for relative in ("AGENTS.md", "harness/HARNESS.md", "harness/skills/ai-erp/SKILL.md"):
        text = (root / relative).read_text(encoding="utf-8")
        if "ui-ux-designer" not in text:
            errors.append(f"UI routing entry missing ui-ux-designer: {relative}")
    for agent in spec["agents"]:
        path = root / f".codex/agents/{spec['harness']['id']}-{agent['id']}.toml"
        config = tomllib.loads(path.read_text(encoding="utf-8"))
        if {"model", "model_reasoning_effort"}.intersection(config):
            errors.append(f"project model inheritance forbids an agent override: {path.name}")
    config = tomllib.loads((root / ".codex/config.toml").read_text(encoding="utf-8"))
    if {"model", "model_reasoning_effort"}.intersection(config) or {
        "default_subagent_model", "default_subagent_reasoning_effort"
    }.intersection(config.get("agents", {})):
        errors.append("project model inheritance forbids a project config override")
    return errors


def verify_tracked(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=root, check=True, capture_output=True
    )
    tracked = set(result.stdout.decode("utf-8").split("\0"))
    spec = json.loads((root / "harness/harness-spec.json").read_text(encoding="utf-8"))
    paths = [root / path for path in (
        "AGENTS.md", ".codex/config.toml", "scripts/verify-harness.py",
        "scripts/test_verify_harness.py", "scripts/smoke-ux-skills.py",
    )]
    paths.extend(root / f".codex/agents/{spec['harness']['id']}-{agent['id']}.toml" for agent in spec["agents"])
    paths.extend(root / f".agents/skills/{skill['id']}/SKILL.md" for skill in spec["skills"])
    for directory in ("harness", "vendor/ux-skills", "scripts/vendor/harness-factory"):
        paths.extend(path for path in (root / directory).rglob("*") if path.is_file())
    errors = []
    for path in paths:
        relative = path.relative_to(root).as_posix()
        if "__pycache__" in path.parts or path.suffix == ".pyc":
            continue
        if relative not in tracked:
            errors.append(f"harness file is not tracked by Git: {relative}")
    return errors


def verify_project(root: Path, require_tracked: bool = False) -> list[str]:
    root = root.resolve()
    validator = ProjectValidator(root, root / "harness/harness-spec.json")
    errors = validator.validate()
    if not errors:
        errors.extend(verify_routing(root, validator.spec))
    errors.extend(verify_vendor(root))
    if require_tracked:
        errors.extend(verify_tracked(root))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--require-tracked", action="store_true", help="also require every harness artifact in the Git index")
    args = parser.parse_args()
    try:
        errors = verify_project(args.root, args.require_tracked)
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as exc:
        errors = [f"cannot verify harness: {exc}"]
    if errors:
        print("Harness verification failed:\n- " + "\n- ".join(errors), file=sys.stderr)
        return 1
    print("Harness verification passed: schema, permissions, DAG, Codex parity, UI routing, and pinned skill bytes.")
    print("Codex model/effort inherit from the parent; no model override is installed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

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
        # Keep native model keys optional here so project policy can choose explicit values;
        # all other schema, permission, path, and parity checks stay unchanged.
        if re.fullmatch(r"Codex agent [a-z0-9-]+\.toml", label):
            required = required - {"model", "model_reasoning_effort"}
        super().check_keys(value, label, required, allowed)


def verify_vendor(root: Path) -> list[str]:
    errors: list[str] = []
    vendor_root = root / "vendor/ux-skills"
    lock = json.loads((vendor_root / "skills.lock.json").read_text(encoding="utf-8"))
    expected_ids = {
        "frontend-design",
        "ui-ux-pro-max",
        "web-design-guidelines",
        "web-interface-guidelines-reference",
    }
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


def verify_templates(root: Path, spec: dict) -> list[str]:
    required = [
        root / "harness/templates/IMPLEMENTATION-CONTRACT.md",
        root / "harness/templates/IMPLEMENTATION-RESULT.md",
        root / "harness/evaluation/TASK-REVIEW-RUBRIC.md",
    ]
    errors = [f"missing required contract template: {path.relative_to(root)}"
            for path in required
            if not path.is_file()]
    contract = root / "harness/templates/IMPLEMENTATION-CONTRACT.md"
    result = root / "harness/templates/IMPLEMENTATION-RESULT.md"
    rubric = root / "harness/evaluation/TASK-REVIEW-RUBRIC.md"
    required_text = {
        contract: ("contract revision", "permitted files", "acceptance criteria", "ready-to-implement"),
        result: ("contract revision", "Selected model", "Invocation", "ready-for-review", "Checks not run"),
        rubric: ("task-review", "gpt-5.3-codex-spark", "gpt-5.6-luna", "independent reviewer"),
    }
    for path, markers in required_text.items():
        if path.is_file():
            text = path.read_text(encoding="utf-8")
            missing = [marker for marker in markers if marker.lower() not in text.lower()]
            if missing:
                errors.append(f"template is missing required fields: {path.relative_to(root)} ({', '.join(missing)})")
    return errors


def verify_roles(root: Path, spec: dict) -> list[str]:
    errors: list[str] = []
    agents = {agent["id"]: agent for agent in spec["agents"]}
    required_agents = {"router", "ui-ux-designer", "reviewer", "implementer"}
    if set(agents) != required_agents:
        missing = required_agents - set(agents)
        extra = set(agents) - required_agents
        if missing:
            errors.append(f"required roles missing: {sorted(missing)}")
        if extra:
            errors.append(f"unexpected roles present: {sorted(extra)}")
    required_tiers = {
        "router": "deep",
        "ui-ux-designer": "deep",
        "reviewer": "deep",
        "implementer": "fast",
    }
    expected_caps = {
        "router": {"routing", "verification"},
        "ui-ux-designer": {"ui-planning"},
        "reviewer": {"verification", "verdict", "defect-counting"},
        "implementer": {"execution", "verification"},
    }
    for role_id, expected in required_tiers.items():
        agent = agents.get(role_id)
        if not agent:
            continue
        if agent.get("model_tier") != expected:
            errors.append(f"{role_id} must use model_tier {expected}")
        if set(agent.get("capabilities", [])) != expected_caps[role_id]:
            errors.append(f"{role_id} capabilities must be {sorted(expected_caps[role_id])}")
        if role_id == "implementer" and "ALL code, tests, and behavior-affecting configuration regardless of path" not in agent.get("description", ""):
            errors.append("implementer role text must cover all code/tests/config scope.")
    implementer_text = (root / "harness/team/agents/implementer.md").read_text(encoding="utf-8") if (root / "harness/team/agents/implementer.md").is_file() else ""
    for phrase in ("no unresolved design decisions", "Do not push", "re-delegate", "final approval", "permission", "scope ambiguity"):
        if phrase.lower() not in implementer_text.lower():
            errors.append(f"implementer role must state boundary: {phrase}")
    return errors


def verify_model_and_paths(root: Path, spec: dict) -> list[str]:
    errors: list[str] = []
    for agent in spec["agents"]:
        config_path = root / f".codex/agents/{spec['harness']['id']}-{agent['id']}.toml"
        config = tomllib.loads(config_path.read_text(encoding="utf-8"))
        if agent["id"] == "implementer":
            if config.get("model") not in {"gpt-5.3-codex-spark", "gpt-5.6-luna"}:
                errors.append("implementer must use gpt-5.3-codex-spark or approved gpt-5.6-luna fallback")
            if config.get("model_reasoning_effort") != "high":
                errors.append("implementer must use model_reasoning_effort=high")
            if config.get("sandbox_mode") != "workspace-write":
                errors.append("implementer must use workspace-write sandbox")
        else:
            if {"model", "model_reasoning_effort"}.intersection(config):
                errors.append(f"model override is forbidden for {agent['id']}: {config_path.name}")
    cfg = tomllib.loads((root / ".codex/config.toml").read_text(encoding="utf-8"))
    if {"model", "model_reasoning_effort"}.intersection(cfg) or {
        "default_subagent_model", "default_subagent_reasoning_effort"
    }.intersection(cfg.get("agents", {})):
        errors.append("project model inheritance must remain parent-inherited; remove code-level overrides")
    return errors


def verify_routing(root: Path, spec: dict) -> list[str]:
    errors: list[str] = []
    skill_ids = {skill.get("id") for skill in spec.get("skills", [])}
    required_skill_ids = {
        "ai-erp", "ai-erp-eval", "ai-erp-verify", "ai-erp-implement",
        "ai-erp-ui-ux", "ai-erp-frontend-design", "ai-erp-ui-ux-pro-max",
        "ai-erp-web-design-guidelines",
    }
    missing_skills = required_skill_ids - skill_ids
    if missing_skills:
        errors.append(f"required workflow skills missing: {sorted(missing_skills)}")
    edges = {(item["from"], item["to"]) for item in spec["orchestration"]["handoffs"]}
    router_implementation = next(
        (item for item in spec["orchestration"]["handoffs"]
         if item.get("from") == "router" and item.get("to") == "implementer"),
        None,
    )
    if not router_implementation or not all(
        phrase in router_implementation.get("when", "").lower()
        for phrase in ("non-ui", "parent-owned contract", "reviewer-to-implementer")
    ):
        errors.append("router -> implementer edge must be explicitly non-UI and contract-gated")
    required_edges = {
        ("router", "ui-ux-designer"),
        ("ui-ux-designer", "reviewer"),
        ("reviewer", "implementer"),
        ("router", "implementer"),
    }
    non_ui_edges = {("router", "implementer")}
    if not required_edges.issubset(edges):
        errors.append("UI routing must pass from router through ui-ux-designer to reviewer")
    if not non_ui_edges.issubset(edges):
        errors.append("non-UI implementation must include router -> implementer")
    forbidden = {
        ("router", "reviewer"),
        ("ui-ux-designer", "implementer"),
        ("implementer", "router"),
        ("implementer", "reviewer"),
    }
    if any(edge in edges for edge in forbidden):
        errors.append("forbidden routing shortcut/back edge detected")

    for skill in spec["skills"]:
        skill_id = skill["id"]
        expected_skill_roles = {
            "ai-erp": ("entry", "router", "task-review"),
            "ai-erp-eval": ("evaluation", "reviewer", "task-review"),
            "ai-erp-verify": ("verification", "reviewer", "harness-structure"),
            "ai-erp-implement": ("domain", "implementer", "task-review"),
            "ai-erp-ui-ux": ("domain", "ui-ux-designer", "ui-plan-review"),
            "ai-erp-frontend-design": ("domain", "ui-ux-designer", "ui-plan-review"),
            "ai-erp-ui-ux-pro-max": ("domain", "ui-ux-designer", "ui-plan-review"),
            "ai-erp-web-design-guidelines": ("domain", "ui-ux-designer", "ui-plan-review"),
        }
        expected = expected_skill_roles.get(skill_id)
        if expected:
            actual = (skill.get("kind"), skill.get("entry_agent"), skill.get("evaluator"))
            if actual != expected:
                errors.append(f"skill role/evaluator mapping mismatch: {skill_id}")
        if skill["kind"] == "domain":
            if skill_id == "ai-erp-implement" and skill.get("entry_agent") != "implementer":
                errors.append(f"implementation skill must enter implementer: {skill_id}")
            elif skill_id != "ai-erp-implement" and "ui-ux" in skill.get("domains", []) and skill.get("entry_agent") != "ui-ux-designer":
                errors.append(f"UI skill must enter ui-ux-designer: {skill_id}")
            if skill_id != "ai-erp-implement" and "ui-ux" in skill.get("domains", []) and skill.get("evaluator") != "ui-plan-review":
                errors.append(f"UI skill must use ui-plan-review: {skill_id}")

        evaluator = skill.get("evaluator")
        if skill_id in {"ai-erp", "ai-erp-eval", "ai-erp-implement"}:
            if evaluator != "task-review":
                errors.append(f"implementation or entry skill must use task-review: {skill_id}")
        if skill_id == "ai-erp-verify" and evaluator != "harness-structure":
            errors.append("ai-erp-verify must use harness-structure")

    implementation_domain = None
    for domain in spec["domains"]:
        if domain["id"] == "implementation":
            implementation_domain = domain
            break
    required_paths = {"frontend", "backend", "scripts", "infra", ".github", "package.json", "Dockerfile"}
    if not implementation_domain:
        errors.append("missing implementation domain")
    else:
        domain_paths = set(implementation_domain.get("paths", []))
        if not required_paths.issubset(domain_paths):
            errors.append("implementation domain missing required paths")

    evaluator_ids = {ev["id"] for ev in spec["evaluators"]}
    for eval_id in {"task-review", "ui-plan-review", "harness-structure"}:
        if eval_id not in evaluator_ids:
            errors.append(f"required evaluator missing: {eval_id}")
    for eval_rule in spec["evaluators"]:
        if eval_rule.get("owner") == "implementer":
            errors.append(f"implementer cannot own evaluator: {eval_rule.get('id')}")
        if eval_rule["id"] == "task-review":
            if eval_rule.get("owner") != "reviewer" or eval_rule.get("runner") != "router":
                errors.append("task-review must be owned by reviewer and run by router")
            pass_condition = eval_rule.get("pass_condition", "").lower()
            required_phrases = ("every applicable required criterion", "failed or pending", "out-of-scope", "planning-only")
            if not all(phrase in pass_condition for phrase in required_phrases):
                errors.append("task-review pass condition must reject failed/pending required criteria and limit N/A to out-of-scope cases")
        if eval_rule["id"] == "ui-plan-review":
            if eval_rule.get("runner") != "router":
                errors.append("ui-plan-review must be run by router")

    for relative in (
        "AGENTS.md",
        "harness/HARNESS.md",
        "harness/skills/ai-erp/SKILL.md",
        "harness/team/agents/ui-ux-designer.md",
        "harness/team/agents/implementer.md",
    ):
        text = (root / relative).read_text(encoding="utf-8")
        if "implementer" not in text:
            errors.append(f"routing or model split missing: {relative}")

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
        errors.extend(verify_roles(root, validator.spec))
        errors.extend(verify_routing(root, validator.spec))
        errors.extend(verify_templates(root, validator.spec))
        errors.extend(verify_model_and_paths(root, validator.spec))
    errors.extend(verify_vendor(root))
    if require_tracked:
        errors.extend(verify_tracked(root))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--require-tracked", action="store_true", help="also require every harness artifact in Git")
    args = parser.parse_args()
    try:
        errors = verify_project(args.root, args.require_tracked)
    except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as exc:
        errors = [f"cannot verify harness: {exc}"]
    if errors:
        print("Harness verification failed:\n- " + "\n- ".join(errors), file=sys.stderr)
        return 1
    print("Harness verification passed: schema, permissions, DAG, Codex parity, UI routing, and pinned skill bytes.")
    print("Router/ui-ux-designer/reviewer inherit native model settings; implementer defaults to gpt-5.3-codex-spark/high with explicit gpt-5.6-luna/high fallback evidence.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

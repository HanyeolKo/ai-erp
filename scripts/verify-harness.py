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

UI_REVIEWER_IMPLEMENTER_WHEN = "A UI plan has passed independent ui-plan-review and the parent has completed the implementation contract."
PRODUCT_PLANNER_UI_WHEN = "An accepted product plan requires screen structure or interaction decisions; parent dispatches after increment-plan-review."
REVIEWER_RELEASE_WHEN = "Accepted implementation evidence and a complete parent release assignment require release preparation or authorized execution."
ROUTER_IMPLEMENTER_WHEN = "A non-UI code, test, or behavior-affecting configuration request is complete against a parent-owned contract; UI implementation uses the reviewer-to-implementer prerequisite instead."
UI_HANDOFF_ARTIFACTS = {
    "harness/templates/SCREEN-PLAN.md",
    "harness/templates/UI-REVIEW.md",
    "harness/templates/IMPLEMENTATION-CONTRACT.md",
    "harness/templates/IMPLEMENTATION-RESULT.md",
}
REQUIRED_HANDOFF_ARTIFACTS = {
    ("reviewer", "implementer"): UI_HANDOFF_ARTIFACTS,
    ("router", "implementer"): {
        "harness/templates/IMPLEMENTATION-CONTRACT.md",
        "harness/templates/IMPLEMENTATION-RESULT.md",
    },
    ("product-planner", "ui-ux-designer"): {
        "harness/templates/INCREMENT-PLAN.md",
        "harness/templates/SCREEN-PLAN.md",
    },
    ("reviewer", "release-manager"): {
        "harness/templates/INCREMENT-RECORD.md",
        "harness/templates/RELEASE-CONTRACT.md",
        "harness/templates/RELEASE-RESULT.md",
    },
}
EXTERNAL_STOP_RULE = "If a required external prerequisite is missing or unknown, stop the affected task before dependent edits or execution and return a BLOCKER-REPORT to the parent."
EXTERNAL_NO_RETRY_RULE = "At most three total attempts apply only to repairable implementation defects, including the initial attempt and any escalation. External blockers stop immediately, consume no retry attempt, and cannot authorize model escalation or a model sweep."
EXTERNAL_GOAL_RULE = "Goal-mode continuation does not override external prerequisites; a blocked task remains blocked until evidence resolves the blocker."
VERIFICATION_POLICY = "harness/policies/VERIFICATION.json"
VERIFICATION_TIERS = {"low", "standard", "high"}


def normalized(value: str) -> str:
    return " ".join(value.split())


def has_unnegated_rule(text: str, rule: str) -> bool:
    """Require a complete, operative rule sentence rather than an instructed exception."""
    haystack = normalized(text).lower()
    needle = normalized(rule).lower()
    start = 0
    while True:
        index = haystack.find(needle, start)
        if index < 0:
            return False
        prefix = haystack[max(0, index - 160):index]
        suffix = haystack[index + len(needle):index + len(needle) + 160]
        preceding = prefix.rstrip()
        at_rule_boundary = (
            not preceding
            or preceding.endswith((".", "!", "?", ";", "-", "#"))
            # The task-review rubric uses a declarative policy label followed by
            # a colon.  Treat only labels ending in a policy subject as a boundary.
            or bool(re.search(r"\b(?:contract|policy|requirement|readiness)\s*:\s*$", preceding))
        )
        target = r"(?:this|the|that|next|following)?\s*(?:rule|requirement|restriction|policy|directive)"
        action = r"(?:enforce|apply|follow|use|honor|obey)"
        negating_prefix = re.search(
            rf"(?:(?:do not|don't|never)\s+{action}\s+{target}|(?:ignore|disregard)\s+(?:the\s+)?{target})\s*:\s*$",
            prefix,
        )
        negating_suffix = re.match(
            rf"\s*(?:(?:do not|don't|never)\s+{action}\s+{target}|(?:ignore|disregard)\s+(?:the\s+)?{target})",
            suffix,
        )
        if at_rule_boundary and not negating_prefix and not negating_suffix:
            return True
        start = index + len(needle)


def has_exact_operative_sentence(text: str, sentence: str) -> bool:
    """Require the approved sentence as an operative rule, not quoted/negated prose."""
    haystack = normalized(text).lower()
    needle = normalized(sentence).lower()
    for match in re.finditer(re.escape(needle), haystack):
        prefix = haystack[max(0, match.start() - 120):match.start()]
        suffix = haystack[match.end():match.end() + 120]
        negated_prefix = (
            re.search(r"(?:do not|don't|never)\s+(?:enforce|apply|follow|use)\s+this\s+(?:rule|requirement|restriction)\s*:?\s*$", prefix)
            or re.search(r"(?:ignore|disregard)\s+(?:the\s+)?(?:rule|requirement|restriction)(?:\s+that)?\s*:?\s*$", prefix)
            or re.search(r"(?:ignore|disregard)\s+this\s+(?:rule|requirement|restriction)\s*:?\s*$", prefix)
        )
        if negated_prefix:
            continue
        if re.match(r"\s*(?:do not|don't|never)\s+(?:enforce|apply|follow|use)\s+this\s+(?:rule|requirement|restriction)", suffix) or re.match(r"\s*(?:ignore|disregard)\s+(?:the\s+)?(?:rule|requirement|restriction)(?:\s+that)?", suffix):
            continue
        return True
    return False


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


ProjectValidator = factory.Validator


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
        root / "harness/policies/MODEL-ORCHESTRATION.md",
        root / "harness/templates/MODEL-USAGE-RECORD.md",
        root / "harness/templates/MODEL-ESCALATION.md",
        root / "harness/evaluation/TASK-REVIEW-RUBRIC.md",
        root / "harness/templates/TASK-ASSIGNMENT.md",
        root / "harness/workflows/DELEGATION-PROTOCOL.md",
        root / "harness/templates/INCREMENT-PLAN.md",
        root / "harness/templates/INCREMENT-REVIEW.md",
        root / "harness/evaluation/INCREMENT-PLAN-RUBRIC.md",
        root / "harness/templates/RELEASE-CONTRACT.md",
        root / "harness/templates/RELEASE-RESULT.md",
        root / "harness/templates/RELEASE-REVIEW.md",
        root / "harness/templates/INCREMENT-RECORD.md",
        root / "harness/evaluation/RELEASE-REVIEW-RUBRIC.md",
        root / "harness/workflows/VERIFICATION-MATRIX.md",
        root / "harness/workflows/RELEASE-FLOW.md",
    ]
    errors = []
    for path in required:
        if path.is_file():
            continue
        relative = path.relative_to(root)
        if path.name == "TASK-ASSIGNMENT.md":
            errors.append(f"missing assignment template: {relative}")
        elif path.name == "DELEGATION-PROTOCOL.md":
            errors.append(f"missing delegation protocol: {relative}")
        else:
            errors.append(f"missing required contract template: {relative}")
    contract = root / "harness/templates/IMPLEMENTATION-CONTRACT.md"
    result = root / "harness/templates/IMPLEMENTATION-RESULT.md"
    rubric = root / "harness/evaluation/TASK-REVIEW-RUBRIC.md"
    assignment = root / "harness/templates/TASK-ASSIGNMENT.md"
    protocol = root / "harness/workflows/DELEGATION-PROTOCOL.md"
    required_text = {
        contract: ("contract revision", "increment id", "plan revision", "plan/review paths", "permitted files", "acceptance criteria", "model reason", "context manifest", "context budget", "output budget", "fork/reuse", "usage availability", "ready-to-implement"),
        result: ("contract revision", "Selected model", "Invocation", "model reason", "context manifest", "context budget", "output budget", "fork/reuse", "usage availability", "ready-for-review", "Checks not run"),
        rubric: ("task-review", "gpt-5.3-codex-spark", "gpt-5.6-luna", "independent reviewer"),
        root / "harness/templates/INCREMENT-PLAN.md": ("increment id", "plan revision", "users", "deferred scope", "API", "acceptance criteria"),
        root / "harness/templates/INCREMENT-REVIEW.md": ("increment id", "plan revision", "independent evaluator", "verdict", "checks not run"),
        root / "harness/evaluation/INCREMENT-PLAN-RUBRIC.md": ("product scope", "cross-layer impact", "revision safety", "independent evidence"),
        root / "harness/templates/RELEASE-CONTRACT.md": ("increment", "reviewed commit", "same-sha", "authorization", "rollback"),
        root / "harness/templates/RELEASE-RESULT.md": ("matching task", "increment", "reviewed SHA", "active release", "observation", "operator-action-required", "checks not run"),
        root / "harness/policies/MODEL-ORCHESTRATION.md": ("gpt-6-astra", "gpt-5.6-luna", "gpt-5.3-codex-spark", "selection reason", "context manifest", "context budget", "output budget", "fork/reuse", "usage availability", "max_parallelism", "max_delegation_depth"),
        root / "harness/templates/MODEL-USAGE-RECORD.md": ("selected model", "reasoning effort", "selection reason", "availability evidence", "context manifest", "context budget", "output budget", "fork/reuse", "token usage", "cache usage"),
        root / "harness/templates/MODEL-ESCALATION.md": ("task and contract revision", "role", "failed attempt", "expected/actual evidence", "failure classification", "previous model", "selected next model", "parent decision", "remaining attempts", "stop condition", "separate independent reviewer", "self-escalation"),
        root / "harness/templates/RELEASE-REVIEW.md": ("release-review", "increment", "revision", "mode", "verdict", "checks not run"),
        root / "harness/templates/INCREMENT-RECORD.md": ("increment id", "plan revision", "commit", "release", "feedback"),
        root / "harness/evaluation/RELEASE-REVIEW-RUBRIC.md": ("same-sha", "authorization", "data safety", "operator-action-required"),
        root / "harness/workflows/VERIFICATION-MATRIX.md": ("frontend", "backend", "database", "cwd", "not run"),
        root / "harness/workflows/RELEASE-FLOW.md": ("same-sha", "authorization", "operator-action-required", "not-requested"),
    }
    for path, markers in required_text.items():
        if path.is_file():
            text = path.read_text(encoding="utf-8")
            missing = [marker for marker in markers if marker.lower() not in text.lower()]
            if missing:
                errors.append(f"template is missing required fields: {path.relative_to(root)} ({', '.join(missing)})")
    if assignment.is_file():
        markers = ("task id", "increment id", "plan revision", "contract revision", "sender", "recipient role", "acceptance", "stop/return", "model reason", "context manifest", "context budget", "output budget", "fork/reuse", "usage availability", "model escalation")
        missing = [marker for marker in markers if marker not in assignment.read_text(encoding="utf-8").lower()]
        if missing:
            errors.append(f"assignment template is missing required fields: {', '.join(missing)}")
    if protocol.is_file():
        markers = ("parent owns", "acknowledges", "blocked", "ready-for-review", "never self-approve", "three total attempts")
        missing = [marker for marker in markers if marker not in protocol.read_text(encoding="utf-8").lower()]
        if missing:
            errors.append(f"delegation protocol is missing required fields: {', '.join(missing)}")
    return errors


def verify_verification_policy(root: Path) -> list[str]:
    """Validate the small machine policy that controls proportionate verification."""
    errors: list[str] = []
    policy_path = root / VERIFICATION_POLICY
    record_path = root / "harness/templates/TASK-RECORD.md"
    if not policy_path.is_file():
        errors.append(f"missing verification policy: {VERIFICATION_POLICY}")
        return errors
    try:
        policy = json.loads(policy_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"verification policy is not valid JSON: {exc}")
        return errors
    if policy.get("schema_version") != "1.0":
        errors.append("verification policy must use schema_version 1.0")
    selection = policy.get("selection")
    if not isinstance(selection, dict):
        errors.append("verification policy must define parent-owned risk selection")
    else:
        if selection.get("risk_tiers") != ["low", "standard", "high"]:
            errors.append("verification policy must define low, standard, and high risk tiers")
        if selection.get("selected_by") != "parent":
            errors.append("risk tier must be selected by parent")
        if selection.get("worker_may_downgrade") is not False:
            errors.append("worker risk downgrade must be disabled")
        if selection.get("ambiguity_action") != "return-to-parent":
            errors.append("risk ambiguity must return to parent")
    tiers = policy.get("tiers")
    if not isinstance(tiers, dict) or set(tiers) != VERIFICATION_TIERS:
        errors.append("verification policy must define exactly low, standard, and high tiers")
        tiers = {}
    low = tiers.get("low", {})
    standard = tiers.get("standard", {})
    high = tiers.get("high", {})
    expected_checks = {
        "low": ["applicable-quick-checks"],
        "standard": ["changed-area-tests"],
        "high": ["relevant-integration-checks"],
    }
    for tier_name, expected in expected_checks.items():
        if tiers.get(tier_name, {}).get("checks") != expected:
            errors.append(f"{tier_name} risk checks must remain policy-defined")
    if low.get("parent_acceptance_required") is not True:
        errors.append("low risk must require parent acceptance")
    low_review = low.get("independent_review", {})
    if low_review.get("required") is not False or low_review.get("default") != "none":
        errors.append("low risk must have no independent reviewer by default")
    if standard.get("parent_acceptance_required") is not True:
        errors.append("standard risk must require parent acceptance")
    standard_review = standard.get("independent_review", {})
    if standard_review.get("required") is not False or standard_review.get("default") != "optional":
        errors.append("standard review must be optional")
    if (standard_review.get("role"), standard_review.get("model"), standard_review.get("effort")) != (
        "reviewer", "gpt-5.6-sol", "medium"
    ):
        errors.append("optional standard review must use reviewer Sol/medium")
    when = standard_review.get("when")
    if not isinstance(when, list) or set(when) != {"uncertainty", "cross-module-impact", "explicit-request"}:
        errors.append("standard optional review triggers must be explicit")
    if high.get("parent_acceptance_required") is not True:
        errors.append("high risk must require parent acceptance")
    required_high_triggers = {
        "authentication", "authorization", "sensitive-data", "db-schema-or-migration",
        "destructive-operation", "deployment-or-recovery", "external-activation", "core-harness-guard",
    }
    if not isinstance(high.get("triggers"), list) or set(high.get("triggers", [])) != required_high_triggers or len(high.get("triggers", [])) != len(required_high_triggers):
        errors.append("high risk triggers must preserve the complete safety trigger list")
    high_review = high.get("independent_review", {})
    if (high_review.get("required"), high_review.get("role"), high_review.get("model"), high_review.get("effort")) != (
        True, "reviewer", "gpt-6-astra", "high"
    ):
        errors.append("high risk must require independent reviewer Astra/high")
    if not isinstance(high.get("reject_without"), list) or set(high["reject_without"]) != {
        "independent-review-result", "relevant-integration-checks"
    }:
        errors.append("high risk must reject missing independent review or relevant integration checks")
    execution = policy.get("execution")
    required_execution = {
        "iteration": "targeted-relevant-checks",
        "batch_boundary": "run-full-applicable-suite-once-after-behavior-affecting-batch",
        "ux_smoke": "only-when-ux-guidance-search-or-runtime-changes",
        "provider_preflight": "only-when-provider-adapters-or-projections-change",
        "git_tracking": "only-when-tracking-or-delivery-is-requested",
        "reuse": "reviewers read valid existing evidence and rerun only changed-invalidated-or-inadequate checks",
    }
    if not isinstance(execution, dict):
        errors.append("verification policy must define conditional execution triggers")
    else:
        for key, expected in required_execution.items():
            if execution.get(key) != expected:
                errors.append(f"verification execution guard mismatch: {key}")
        if execution.get("raw_evidence") != "capture-command-array-cwd-exit-stdout-stderr-once":
            errors.append("verification policy must require exact raw command evidence")
    preserved = policy.get("preserved_gates")
    for key in ("ui", "product_planning", "release", "external", "access"):
        if not isinstance(preserved, dict) or not preserved.get(key):
            errors.append(f"verification policy must preserve {key} safeguards")
    record = policy.get("record")
    if not isinstance(record, dict) or record.get("canonical_template") != "harness/templates/TASK-RECORD.md":
        errors.append("verification policy must name TASK-RECORD.md as canonical record")
    if not isinstance(record, dict) or record.get("low_standard_single_record") is not True:
        errors.append("verification policy must allow one compact record for low/standard work")
    if not isinstance(record, dict) or record.get("escalation_only") != "MODEL-ESCALATION.md-is-required-only-for-real-capability-escalation":
        errors.append("verification policy must reserve MODEL-ESCALATION.md for real capability escalation")
    plan_reviews = policy.get("plan_reviews")
    expected_plan_reviews = {
        "normal": {
            "required": True,
            "role": "reviewer",
            "model": "gpt-5.6-sol",
            "effort": "medium",
            "gates": ["increment-plan-review", "ui-plan-review"],
        },
        "high": {
            "required": True,
            "role": "reviewer",
            "model": "gpt-6-astra",
            "effort": "high",
            "gates": ["increment-plan-review", "ui-plan-review", "release-review"],
        },
    }
    if plan_reviews != expected_plan_reviews:
        errors.append("verification policy must define normal Sol/medium and high Astra/high plan review selection")
    if not record_path.is_file():
        errors.append("missing compact task record template: harness/templates/TASK-RECORD.md")
    else:
        record_text = record_path.read_text(encoding="utf-8").lower()
        markers = (
            "task/scope", "assignment acknowledgement", "risk tier", "parent reason", "applicable gate", "selected role",
            "model", "effort", "relevant checks", "checks not run", "parent disposition",
            "independent review", "workers never downgrade", "self-approve",
        )
        missing = [marker for marker in markers if marker not in record_text]
        if missing:
            errors.append(f"task record missing required fields: {', '.join(missing)}")
    return errors


def verify_roles(root: Path, spec: dict) -> list[str]:
    errors: list[str] = []
    agents = {agent["id"]: agent for agent in spec["agents"]}
    required_agents = {"router", "ui-ux-designer", "reviewer", "implementer", "product-planner", "release-manager"}
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
        "product-planner": "deep",
        "release-manager": "fast",
    }
    expected_caps = {
        "router": {"routing", "verification"},
        "ui-ux-designer": {"ui-planning"},
        "reviewer": {"verification", "verdict", "defect-counting"},
        "implementer": {"execution", "verification"},
        "product-planner": {"product-planning"},
        "release-manager": {"release-preparation", "release-operations", "verification"},
    }
    for role_id, expected in required_tiers.items():
        agent = agents.get(role_id)
        if not agent:
            continue
        if agent.get("model_tier") != expected:
            errors.append(f"{role_id} must use model_tier {expected}")
        if set(agent.get("capabilities", [])) != expected_caps[role_id]:
            errors.append(f"{role_id} capabilities must be {sorted(expected_caps[role_id])}")
        expected_access = "read-only" if role_id in {"router", "reviewer"} else "workspace-write"
        if agent.get("access") != expected_access:
            errors.append(f"{role_id} access must be {expected_access}")
        if role_id == "implementer" and "ALL code, tests, and behavior-affecting configuration regardless of path" not in agent.get("description", ""):
            errors.append("implementer role text must cover all code/tests/config scope.")
    implementer_text = (root / "harness/team/agents/implementer.md").read_text(encoding="utf-8") if (root / "harness/team/agents/implementer.md").is_file() else ""
    for phrase in ("no unresolved design decisions", "Do not push", "re-delegate", "final approval", "permission", "scope ambiguity"):
        if phrase.lower() not in implementer_text.lower():
            errors.append(f"implementer role must state boundary: {phrase}")
    planner_text = (root / "harness/team/agents/product-planner.md").read_text(encoding="utf-8") if (root / "harness/team/agents/product-planner.md").is_file() else ""
    for phrase in ("product-planning", "screen", "code", "verdict", "re-delegate", "TASK-ASSIGNMENT"):
        if phrase.lower() not in planner_text.lower():
            errors.append(f"product-planner role must state boundary: {phrase}")
    release_text = (root / "harness/team/agents/release-manager.md").read_text(encoding="utf-8") if (root / "harness/team/agents/release-manager.md").is_file() else ""
    for phrase in ("release-preparation", "release-operations", "source", "tests", "configuration", "same-SHA", "operator-action-required", "re-delegate"):
        if phrase.lower() not in release_text.lower():
            errors.append(f"release-manager role must state boundary: {phrase}")
    return errors


def verify_model_and_paths(root: Path, spec: dict) -> list[str]:
    errors: list[str] = []
    expected_models = {
        "router": ("gpt-5.6-sol", "medium"),
        "ui-ux-designer": ("gpt-5.6-sol", "medium"),
        "reviewer": ("gpt-6-astra", "high"),
        "implementer": ("gpt-5.6-luna", "high"),
        "product-planner": ("gpt-5.6-sol", "medium"),
        "release-manager": ("gpt-5.6-luna", "high"),
    }
    for agent in spec["agents"]:
        config_path = root / f".codex/agents/{spec['harness']['id']}-{agent['id']}.toml"
        config = tomllib.loads(config_path.read_text(encoding="utf-8"))
        expected_model, expected_effort = expected_models[agent["id"]]
        if config.get("model") != expected_model:
            errors.append(f"{agent['id']} must declare model={expected_model}")
        if config.get("model_reasoning_effort") != expected_effort:
            errors.append(f"{agent['id']} must declare model_reasoning_effort={expected_effort}")
        if config.get("sandbox_mode") != agent.get("access"):
            errors.append(f"{agent['id']} must use {agent.get('access')} sandbox")
    cfg = tomllib.loads((root / ".codex/config.toml").read_text(encoding="utf-8"))
    if cfg.get("model") != "gpt-6-astra":
        errors.append("root config must declare model=gpt-6-astra")
    if cfg.get("model_reasoning_effort") != "high":
        errors.append("root config must declare model_reasoning_effort=high")
    agents_cfg = cfg.get("agents", {})
    if agents_cfg.get("max_threads") != 2:
        errors.append("Codex max_threads must remain exactly 2")
    if agents_cfg.get("max_depth") != 1:
        errors.append("Codex max_depth must remain exactly 1")
    return errors


def verify_model_escalation(root: Path) -> list[str]:
    errors: list[str] = []
    artifact = root / "harness/templates/MODEL-ESCALATION.md"
    policy = root / "harness/policies/MODEL-ORCHESTRATION.md"
    if not artifact.is_file():
        errors.append("missing model escalation artifact: harness/templates/MODEL-ESCALATION.md")
    else:
        text = artifact.read_text(encoding="utf-8").lower()
        required = (
            "task and contract revision", "role", "failed attempt", "expected/actual evidence",
            "failure classification", "previous model", "selected next model", "parent decision",
            "remaining attempts", "stop condition", "separate independent reviewer", "self-escalation",
        )
        missing = [marker for marker in required if marker not in text]
        if missing:
            errors.append(f"model escalation artifact missing required fields: {', '.join(missing)}")
    if not policy.is_file():
        errors.append("missing model orchestration policy")
    else:
        text = policy.read_text(encoding="utf-8").lower()
        required = (
            "gpt-5.6-terra", "gpt-5.6-sol", "three total attempts", "never an executor fallback",
            "external or quota blockers stop", "never justify a model sweep", "workers cannot self-escalate", "self-review", "same-cost alternative",
            "complete, clear contract", "capability/comprehension failure", "ordinary repairable defect", "same-model correction", "renaming a task or revision never resets",
        )
        missing = [marker for marker in required if marker not in text]
        if missing:
            errors.append(f"model escalation policy missing required rules: {', '.join(missing)}")
        if "astra" in text and "never astra execution" not in text:
            errors.append("model escalation policy must reject Astra executor escalation")
        if "astra execution is allowed" in text or "astra may execute" in text:
            errors.append("model escalation policy must reject Astra executor escalation")
        if not has_exact_operative_sentence(text, "A lower Sol role is at its ceiling and returns to the Astra orchestrator for self-review"):
            errors.append("model escalation policy must require Sol-ceiling Astra orchestrator self-review")
        if not has_exact_operative_sentence(text, "Parent-owned executor escalation follows"):
            errors.append("model escalation policy must require parent-owned executor escalation")
        if "parent-owned executor escalation follows" in text and "worker-owned executor escalation follows" in text:
            errors.append("model escalation policy must keep executor escalation parent-owned")
        if not has_exact_operative_sentence(text, "Astra is never an executor fallback"):
            errors.append("model escalation policy must reject Astra executor escalation")
    for relative in (
        "harness/workflows/DELEGATION-PROTOCOL.md",
        "harness/templates/TASK-ASSIGNMENT.md",
        "harness/templates/MODEL-USAGE-RECORD.md",
        "harness/templates/IMPLEMENTATION-RESULT.md",
        "harness/skills/ai-erp-implement/SKILL.md",
        "harness/skills/ai-erp-eval/SKILL.md",
        "harness/evaluation/TASK-REVIEW-RUBRIC.md",
    ):
        path = root / relative
        if not path.is_file() or "model-escalation" not in path.read_text(encoding="utf-8").lower():
            errors.append(f"model escalation reference missing: {relative}")
    authorized_rung_docs = (
        "harness/skills/ai-erp-implement/SKILL.md",
        ".agents/skills/ai-erp-implement/SKILL.md",
        "harness/team/agents/reviewer.md",
        "harness/templates/IMPLEMENTATION-RESULT.md",
        "harness/evaluation/TASK-REVIEW-RUBRIC.md",
    )
    for relative in authorized_rung_docs:
        path = root / relative
        if not path.is_file():
            errors.append(f"authorized model rung reference missing: {relative}")
            continue
        text = path.read_text(encoding="utf-8").lower()
        if ("gpt-5.6-terra" not in text and "terra/medium" not in text) or ("gpt-5.6-sol" not in text and "sol/medium" not in text) or "medium" not in text:
            errors.append(f"authorized model rung effort evidence missing: {relative}")
    skill_text = (root / "harness/skills/ai-erp-implement/SKILL.md").read_text(encoding="utf-8").lower()
    reviewer_text = (root / "harness/team/agents/reviewer.md").read_text(encoding="utf-8").lower()
    if "selected model, high reasoning" in skill_text or "plus high reasoning" in reviewer_text:
        errors.append("implementation model checks must validate the selected authorized rung effort")
    release_result = root / "harness/templates/RELEASE-RESULT.md"
    if not release_result.is_file() or "model-escalation.md" not in release_result.read_text(encoding="utf-8").lower():
        errors.append("release result must link model escalation evidence when applicable")
    return errors


def verify_routing(root: Path, spec: dict) -> list[str]:
    errors: list[str] = []
    skill_ids = {skill.get("id") for skill in spec.get("skills", [])}
    required_skill_ids = {
        "ai-erp", "ai-erp-eval", "ai-erp-verify", "ai-erp-implement",
        "ai-erp-ui-ux", "ai-erp-frontend-design", "ai-erp-ui-ux-pro-max",
        "ai-erp-web-design-guidelines",
        "ai-erp-plan",
        "ai-erp-release",
    }
    missing_skills = required_skill_ids - skill_ids
    if missing_skills:
        errors.append(f"required workflow skills missing: {sorted(missing_skills)}")
    handoffs = spec["orchestration"]["handoffs"]
    edge_pairs = [(item["from"], item["to"]) for item in handoffs]
    edges = set(edge_pairs)
    if len(edge_pairs) != len(edges):
        errors.append("duplicate handoff edge detected")
    router_implementation = next(
        (item for item in spec["orchestration"]["handoffs"]
         if item.get("from") == "router" and item.get("to") == "implementer"),
        None,
    )
    if not router_implementation or normalized(router_implementation.get("when", "")) != normalized(ROUTER_IMPLEMENTER_WHEN):
        errors.append("router -> implementer edge must be explicitly non-UI and contract-gated")
    required_edges = {
        ("router", "ui-ux-designer"),
        ("ui-ux-designer", "reviewer"),
        ("reviewer", "implementer"),
        ("router", "implementer"),
        ("router", "product-planner"),
        ("product-planner", "reviewer"),
        ("product-planner", "ui-ux-designer"),
        ("reviewer", "release-manager"),
    }
    non_ui_edges = {("router", "implementer")}
    if edges != required_edges:
        errors.append("UI routing must contain exactly the eight accepted handoff edges")
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

    expected_conditions = {
        ("router", "implementer"): ROUTER_IMPLEMENTER_WHEN,
        ("reviewer", "implementer"): UI_REVIEWER_IMPLEMENTER_WHEN,
        ("product-planner", "ui-ux-designer"): PRODUCT_PLANNER_UI_WHEN,
        ("reviewer", "release-manager"): REVIEWER_RELEASE_WHEN,
    }
    for pair, expected_when in expected_conditions.items():
        matching = [item for item in handoffs if (item.get("from"), item.get("to")) == pair]
        if not matching or normalized(matching[0].get("when", "")) != normalized(expected_when):
            errors.append(f"{pair[0]} -> {pair[1]} condition must exactly preserve its approved prerequisite")
    for pair, expected_artifacts in REQUIRED_HANDOFF_ARTIFACTS.items():
        edge = next((item for item in handoffs if (item.get("from"), item.get("to")) == pair), None)
        if edge and set(edge.get("artifacts", [])) != expected_artifacts:
            errors.append(f"{pair[0]} -> {pair[1]} handoff artifacts must preserve the mandatory contract/result artifacts")

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
            "ai-erp-plan": ("domain", "product-planner", "increment-plan-review"),
            "ai-erp-release": ("domain", "release-manager", "release-review"),
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
    for eval_id in {"task-review", "increment-plan-review", "release-review", "ui-plan-review", "harness-structure"}:
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
        if eval_rule["id"] == "increment-plan-review":
            if eval_rule.get("owner") != "reviewer" or eval_rule.get("runner") != "router":
                errors.append("increment-plan-review must be owned by reviewer and run by router")
        if eval_rule["id"] == "release-review":
            if eval_rule.get("owner") != "reviewer" or eval_rule.get("runner") != "router":
                errors.append("release-review must be owned by reviewer and run by router")

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


def verify_external_policy(root: Path) -> list[str]:
    errors: list[str] = []
    workflow = root / "harness/workflows/EXTERNAL-DEPENDENCIES.md"
    blocker = root / "harness/templates/BLOCKER-REPORT.md"
    for path, label in ((workflow, "external dependency workflow"), (blocker, "blocker report template")):
        if not path.is_file():
            errors.append(f"missing {label}: {path.relative_to(root)}")
    if workflow.is_file():
        text = workflow.read_text(encoding="utf-8")
        if not has_unnegated_rule(text, EXTERNAL_STOP_RULE):
            errors.append("external stop rule must require immediate stop before dependent work")
        if not has_unnegated_rule(text, EXTERNAL_NO_RETRY_RULE):
            errors.append("external stop rule must prohibit unchanged-blocker retries")
        required = (
            "offline-contract-only", "live-integration", "verified", "missing", "unknown",
            "not-required", "provider/service", "account/tenant/project", "API contract/version",
            "OAuth", "configuration/key names", "external owner", "safe check", "expected result",
            "resolution options", "resume checks", "goal-mode", "BLOCKER-REPORT",
        )
        missing = [marker for marker in required if marker.lower() not in text.lower()]
        if missing:
            errors.append(f"external dependency workflow missing required fields: {', '.join(missing)}")
    if blocker.is_file():
        text = blocker.read_text(encoding="utf-8").lower()
        required = (
            "task id", "increment id", "plan revision", "contract revision", "dependency",
            "target", "sanitized evidence", "classification", "attempted actions",
            "affected scope", "current diff", "external owner", "granted", "resolution options",
            "resume checks", "blocked",
        )
        missing = [marker for marker in required if marker not in text]
        if missing:
            errors.append(f"blocker report template missing required fields: {', '.join(missing)}")

    assignment = root / "harness/templates/TASK-ASSIGNMENT.md"
    contract = root / "harness/templates/IMPLEMENTATION-CONTRACT.md"
    result = root / "harness/templates/IMPLEMENTATION-RESULT.md"
    artifacts = {
        assignment: ("external dependency readiness", "mode", "owner", "resume checks"),
        contract: ("external dependency readiness", "offline-contract-only", "live-integration", "blocked"),
        result: ("external dependency", "blocked", "resume checks", "goal-mode"),
    }
    for path, required in artifacts.items():
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8").lower()
        missing = [marker for marker in required if marker not in text]
        if missing:
            errors.append(f"external readiness fields missing: {path.relative_to(root)} ({', '.join(missing)})")

    required_refs = {
        "harness/evaluation/TASK-REVIEW-RUBRIC.md": (EXTERNAL_STOP_RULE, "blocked"),
        "harness/evaluation/INCREMENT-PLAN-RUBRIC.md": ("external dependency", "owner"),
        "harness/evaluation/RELEASE-REVIEW-RUBRIC.md": ("external dependency", "operator-action-required"),
        "harness/loops/EXECUTION-LOOP.md": (EXTERNAL_STOP_RULE, EXTERNAL_NO_RETRY_RULE, EXTERNAL_GOAL_RULE),
        "harness/loops/EVAL-LOOP.md": (EXTERNAL_STOP_RULE, EXTERNAL_NO_RETRY_RULE, EXTERNAL_GOAL_RULE),
        "harness/recovery/CHECKPOINT.md": ("external blocker", "resume checks"),
        "harness/recovery/RECOVERY-PLAYBOOK.md": (EXTERNAL_STOP_RULE, EXTERNAL_NO_RETRY_RULE, "resume checks"),
        "harness/ledger/JOURNAL-FORMAT.md": ("BLOCKER-REPORT", "external owner", "resume checks"),
    }
    for relative, markers in required_refs.items():
        path = root / relative
        if not path.is_file():
            errors.append(f"missing external policy reference: {relative}")
            continue
        raw_text = path.read_text(encoding="utf-8")
        text = normalized(raw_text).replace("`", "").lower()
        missing = []
        for marker in markers:
            if marker in {EXTERNAL_STOP_RULE, EXTERNAL_NO_RETRY_RULE, EXTERNAL_GOAL_RULE}:
                if not has_unnegated_rule(raw_text, marker):
                    missing.append(marker)
            elif normalized(marker).replace("`", "").lower() not in text:
                missing.append(marker)
        if missing:
            errors.append(f"external policy reference missing: {relative} ({', '.join(missing)})")
    eval_loop = root / "harness/loops/EVAL-LOOP.md"
    if eval_loop.is_file() and normalized(EXTERNAL_GOAL_RULE).replace("`", "").lower() not in normalized(eval_loop.read_text(encoding="utf-8")).replace("`", "").lower():
        errors.append("goal-mode external prerequisite rule must keep blocked tasks blocked")
    if eval_loop.is_file():
        eval_text = normalized(eval_loop.read_text(encoding="utf-8")).lower()
        for marker in ("required-model-capacity blocker", "forward progression requires", "parent acceptance"):
            if marker not in eval_text:
                errors.append(f"evaluation loop missing model-capacity progression rule: {marker}")
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
        errors.extend(verify_verification_policy(root))
        errors.extend(verify_model_and_paths(root, validator.spec))
        errors.extend(verify_model_escalation(root))
        errors.extend(verify_external_policy(root))
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
    print("Astra owns orchestration/final review; Sol owns lower planning/design; Luna owns implementation/release; executor escalation is Luna -> Terra -> Sol and never Astra.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

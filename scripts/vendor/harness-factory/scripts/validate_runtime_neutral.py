#!/usr/bin/env python3
"""Validate a generated runtime-neutral harness and its native adapters."""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
import unicodedata
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError as exc:  # pragma: no cover - Python < 3.11
    raise SystemExit("Python 3.11 or newer is required (tomllib is missing)") from exc

ID_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
LANGUAGE_TAG_RE = re.compile(r"^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$")
FENCE_RE = re.compile(r"^\s*(`{3,}|~{3,})")
DOCUMENT_BUDGET_EXCEPTION_RE = re.compile(
    r"^<!-- document-budget exception: "
    r"(?P<type>[a-z0-9]+(?:-[a-z0-9]+)*) \| "
    r"(?P<reason>[^<>\r\n]+) -->$"
)
MARKDOWN_LINK_TARGET_RE = re.compile(r"\]\([^)\n]*\)")
URL_RE = re.compile(r"https?://\S+")
MACHINE_TOKEN_RE = re.compile(
    r"(?<!\S)(?:"
    r"[A-Za-z]:[\\/]\S+|"
    r"(?:\.{0,2}[\\/])\S+|"
    r"\S+[\\/]\S+|"
    r"--?[A-Za-z0-9][^\s]*|"
    r"[A-Za-z_][A-Za-z0-9_.-]*=[^\s]+"
    r")(?!\S)"
)
MODEL_TIERS = {"fast", "balanced", "deep"}
SCHEMA_VERSIONS = {"1.0", "1.1", "1.2"}
PROFILES = {"core", "adaptive", "governed"}
PROFILE_RANK = {"core": 0, "adaptive": 1, "governed": 2}
LANES = {"control", "execution", "evaluation", "improvement"}
ACCESS = {"read-only", "workspace-write"}
CLAUDE_READ_ONLY_TOOLS = {"Read", "Grep", "Glob"}
CLAUDE_READ_ONLY_DISALLOWED = {"Write", "Edit", "NotebookEdit", "Bash"}
CLAUDE_WORKSPACE_WRITE_TOOLS = {"Read", "Grep", "Glob", "Write", "Edit", "Bash"}
GEMINI_READ_ONLY_TOOLS = {"read_file", "glob", "search_file_content"}
GEMINI_WORKSPACE_WRITE_TOOLS = GEMINI_READ_ONLY_TOOLS | {
    "write_file",
    "replace",
    "run_shell_command",
}
TASK_SKILL_KINDS = {"entry", "evaluation", "verification", "domain"}
HARNESS_SKILL_KINDS = {"harness-evaluation", "improvement"}
MANDATORY_SELF_EVALUATION_EVENTS = {
    "canonical-contract-change",
    "agent-change",
    "skill-change",
    "evaluator-change",
    "adapter-change",
    "coldstart-fail",
    "parity-fail",
}
TARGETED_REASONS = {"cost-regression", "retry-pressure", "deterministic-sample"}
HARNESS_EFFECT_VERDICTS = {"improved", "neutral", "regressed", "inconclusive"}
PROVIDER_REQUIRED_KEYS = {
    "id",
    "display_name",
    "capabilities",
    "root_guidance",
    "skill_root",
    "agent_root",
    "agent_extension",
}
PROVIDER_ALLOWED_KEYS = PROVIDER_REQUIRED_KEYS | {"config"}
REQUIRED_CAPABILITIES = {
    "routing",
    "execution",
    "verification",
    "verdict",
    "defect-counting",
    "improvement",
}
PROFILE_REQUIRED_CAPABILITIES = {
    "core": REQUIRED_CAPABILITIES - {"improvement"},
    "adaptive": REQUIRED_CAPABILITIES,
    "governed": REQUIRED_CAPABILITIES,
}
PROFILE_FORBIDDEN_CAPABILITIES = {
    "core": {"improvement"},
    "adaptive": set(),
    "governed": set(),
}
PROFILE_REQUIRED_SKILL_KINDS = {
    "core": {"entry", "evaluation", "verification"},
    "adaptive": {
        "entry",
        "evaluation",
        "verification",
        "harness-evaluation",
        "improvement",
    },
    "governed": {
        "entry",
        "evaluation",
        "verification",
        "harness-evaluation",
        "improvement",
    },
}
PROFILE_FORBIDDEN_SKILL_KINDS = {
    "core": {"harness-evaluation", "improvement"},
    "adaptive": set(),
    "governed": set(),
}
PROFILE_REQUIRED_EVALUATOR_SCOPES = {
    "core": {"task"},
    "adaptive": {"task", "harness"},
    "governed": {"task", "harness"},
}
PROFILE_FORBIDDEN_EVALUATOR_SCOPES = {
    "core": {"harness"},
    "adaptive": set(),
    "governed": set(),
}
PROFILE_REQUIRED_LOOP_KEYS = {
    "core": {"execution", "evaluation", "fail_threshold"},
    "adaptive": {
        "execution",
        "evaluation",
        "improvement",
        "improvement_owner",
        "fail_threshold",
    },
    "governed": {
        "execution",
        "evaluation",
        "improvement",
        "improvement_owner",
        "fail_threshold",
    },
}
PROFILE_FORBIDDEN_LOOP_KEYS = {
    "core": {"improvement", "improvement_owner", "retro_interval"},
    "adaptive": {"retro_interval"},
    "governed": {"retro_interval"},
}
CONSTRUCTION_MODES = {"create", "improve", "reconcile"}
DECISION_SOURCES = {
    "request",
    "user-answer",
    "repository-confirmed",
    "default-delegated",
    "approved-decision",
}
EXPLICIT_CONFIRMATION_SOURCES = {"request", "user-answer", "approved-decision"}
REQUIRED_INTERVIEW_DECISIONS = {
    "purpose",
    "deliverable_type",
    "task_evaluator",
    "operation_mode",
    "cost_sensitivity",
    "report_language",
    "terminology",
    "runtime_targets",
    "approval_gates",
    "reporting",
}
ADAPTIVE_REASON_CODES = {
    "durable-memory-needed",
    "recurring-workflow",
    "long-running-or-unattended",
    "harness-effect-measurement",
    "evidence-gated-improvement",
}
CORE_REASON_CODES = {"bounded-task-work"}
GOVERNED_REASON_CODES = {
    "audit-or-compliance",
    "developer-understanding-evidence",
    "learning-gate-requested",
}
LOWER_SCOPE_REASON_CODES = {
    "capability-unused",
    "maintenance-cost-exceeds-benefit",
    "project-scope-reduced",
}
PROFILE_REASON_CODES = (
    CORE_REASON_CODES
    | ADAPTIVE_REASON_CODES
    | GOVERNED_REASON_CODES
    | LOWER_SCOPE_REASON_CODES
)
DOCUMENT_BUDGET_EXCEPTION_TYPES = {
    "required-sequential-instruction",
    "atomic-project-contract",
    "higher-routing-overhead",
}
CORE_COMMON_FILES = {
    "HARNESS.md",
    "harness-spec.json",
    "ENVIRONMENT.md",
    "team/TEAM-ARCHITECTURE.md",
    "loops/EXECUTION-LOOP.md",
    "loops/EVAL-LOOP.md",
    "recovery/RECOVERY-PLAYBOOK.md",
    "recovery/CHECKPOINT.md",
    "ledger/JOURNAL-FORMAT.md",
    "ledger/DECISIONS.md",
    "ledger/journal.jsonl",
    "budget/CONTEXT-BUDGET.md",
    "state/state.json",
    "policies/reporting.json",
    "reports/_templates/CHANGE-REPORT.md.tmpl",
}
ADAPTIVE_COMMON_FILES = {
    "memory/INDEX.md",
    "loops/IMPROVE-LOOP.md",
    "loops/HARNESS-EVAL-LOOP.md",
    "evaluation/EVALUATION-CONTRACT.md",
    "evaluation/suites/targeted.json",
    "triggers/check_self_evaluation.py",
    "triggers/record_self_evaluation.py",
    "state/self-evaluation.json",
    "maintenance/COMPONENT-MUTATION-PROTOCOL.md",
}
LEARNING_ASSIST_FILES = {
    "learning-assist/_templates/explanation.md.tmpl",
    "learning-assist/_templates/quiz.json.tmpl",
    "learning-assist/_templates/comprehension.json.tmpl",
}
GOVERNED_CONTROL_FILES = {
    "policies/learning-gate.json",
    "policies/LEARNING-GATE.md",
    "learning/_templates/brief.md.tmpl",
    "learning/_templates/diff-explanation.md.tmpl",
    "learning/_templates/quiz.json.tmpl",
    "learning/_templates/answers.json.tmpl",
    "learning/_templates/verification.json.tmpl",
    "triggers/verify_learning_gate.py",
}
PROFILE_REQUIRED_COMMON_FILES = {
    "core": CORE_COMMON_FILES,
    "adaptive": CORE_COMMON_FILES | ADAPTIVE_COMMON_FILES,
    "governed": (
        CORE_COMMON_FILES
        | ADAPTIVE_COMMON_FILES
        | LEARNING_ASSIST_FILES
        | GOVERNED_CONTROL_FILES
    ),
}
PROFILE_FORBIDDEN_COMMON_FILES = {
    "core": ADAPTIVE_COMMON_FILES | LEARNING_ASSIST_FILES | GOVERNED_CONTROL_FILES,
    "adaptive": LEARNING_ASSIST_FILES | GOVERNED_CONTROL_FILES,
    "governed": set(),
}
TOP_LEVEL_KEYS = {
    "schema_version",
    "profile",
    "harness",
    "runtime_targets",
    "limits",
    "domains",
    "agents",
    "skills",
    "orchestration",
    "evaluators",
    "approval_gates",
    "communication",
    "memory",
    "loops",
    "self_evaluation",
}
MEMORY_STATUSES = {"active", "superseded", "archived", "empty"}
ENGLISH_MEMORY_HEADERS = (
    "ID",
    "Path",
    "Summary",
    "Read when",
    "Source",
    "Last verified",
    "Status",
)
LEGACY_KOREAN_MEMORY_HEADERS = (
    "ID",
    "경로",
    "한 줄 요약",
    "언제 읽나",
    "출처",
    "마지막 검증",
    "상태",
)


def strip_inline_code(text: str) -> str:
    """Remove balanced Markdown code spans without interpreting their contents."""
    result: list[str] = []
    index = 0
    while index < len(text):
        if text[index] != "`":
            result.append(text[index])
            index += 1
            continue
        end_ticks = index
        while end_ticks < len(text) and text[end_ticks] == "`":
            end_ticks += 1
        marker = text[index:end_ticks]
        closing = text.find(marker, end_ticks)
        if closing < 0:
            result.append(marker)
            index = end_ticks
            continue
        result.append(" ")
        index = closing + len(marker)
    return "".join(result)


def non_english_letters(text: str) -> str:
    """Return distinct non-ASCII letters left in human-facing prose."""
    prose = strip_inline_code(text)
    prose = MARKDOWN_LINK_TARGET_RE.sub(" ", prose)
    prose = URL_RE.sub(" ", prose)
    prose = MACHINE_TOKEN_RE.sub(" ", prose)
    return "".join(
        sorted(
            {
                character
                for character in prose
                if unicodedata.category(character).startswith("L")
                and not (
                    "A" <= character <= "Z"
                    or "a" <= character <= "z"
                )
            }
        )
    )


class Validator:
    def __init__(
        self,
        target: Path,
        spec_path: Path,
        construction_mode: str | None = None,
        delta_plan_path: Path | None = None,
    ) -> None:
        self.target = target.resolve()
        self.spec_path = spec_path.resolve()
        self.construction_mode = construction_mode
        self.delta_plan_path = (
            (
                delta_plan_path
                if delta_plan_path.is_absolute()
                else self.target / delta_plan_path
            ).resolve()
            if delta_plan_path
            else None
        )
        self.errors: list[str] = []
        self.receipt_mode = ""
        self.spec: dict = {}
        self.schema_version = ""
        self.profile = ""
        self.namespace = ""
        self.harness_root = self.target / "harness"
        self.provider_contracts: dict[str, dict] = {}
        self.runtime_targets: set[str] = set()
        self.agent_ids: set[str] = set()
        self.skill_ids: set[str] = set()
        self.domain_ids: set[str] = set()
        self.evaluator_ids: set[str] = set()
        self.gate_ids: set[str] = set()
        self.provider_path_preflight_failed = False
        self.max_instruction_lines = 0
        self.target_markdown_lines = 0
        self.max_markdown_lines = 0
        self.construction_receipt_path: Path | None = None
        self.memory_index_path: Path | None = None
        self.memory_max_document_lines = 0
        self.memory_max_summary_chars = 0
    def error(self, message: str) -> None:
        self.errors.append(message)

    def load(self) -> None:
        try:
            value = json.loads(self.spec_path.read_text(encoding="utf-8"))
        except OSError as exc:
            self.error(f"cannot read spec: {exc}")
            return
        except json.JSONDecodeError as exc:
            self.error(f"spec is not valid JSON: {exc}")
            return
        if not isinstance(value, dict):
            self.error("spec must be a JSON object")
            return
        self.spec = value

    def load_provider_contracts(self) -> None:
        provider_root = Path(__file__).resolve().parents[1] / "providers"
        if not provider_root.is_dir():
            self.error(f"provider registry is missing: {provider_root}")
            return
        contract_paths = sorted(provider_root.glob("*/contract.json"))
        if not contract_paths:
            self.error("provider registry contains no contracts")
            return
        for path in contract_paths:
            label = f"provider contract {path.parent.name}"
            try:
                contract = json.loads(path.read_text(encoding="utf-8"))
            except OSError as exc:
                self.error(f"cannot read {label}: {exc}")
                continue
            except json.JSONDecodeError as exc:
                self.error(f"{label} is not valid JSON: {exc}")
                continue
            if not isinstance(contract, dict):
                self.error(f"{label} must be an object")
                continue
            self.check_keys(
                contract,
                label,
                PROVIDER_REQUIRED_KEYS,
                PROVIDER_ALLOWED_KEYS,
            )
            provider_id = self.identifier(contract.get("id"), f"{label}.id")
            if not provider_id:
                continue
            if provider_id != path.parent.name:
                self.error(
                    f"{label}.id must match its directory name: {provider_id!r}"
                )
            if provider_id in self.provider_contracts:
                self.error(f"duplicate provider contract id: {provider_id}")
                continue
            display_name = contract.get("display_name")
            if not isinstance(display_name, str) or not display_name.strip():
                self.error(f"{label}.display_name must be a non-empty string")
            capabilities = self.string_list(
                contract.get("capabilities"), f"{label}.capabilities"
            )
            if not capabilities:
                self.error(f"{label}.capabilities must not be empty")
            if len(capabilities) != len(set(capabilities)):
                self.error(f"{label}.capabilities contains duplicates")
            for capability in capabilities:
                self.identifier(capability, f"{label}.capabilities")
            for key in ("root_guidance", "skill_root", "agent_root"):
                if not self.safe_relative(contract.get(key), f"{label}.{key}"):
                    self.provider_path_preflight_failed = True
            extension = contract.get("agent_extension")
            if (
                not isinstance(extension, str)
                or not extension.startswith(".")
                or "/" in extension
                or "\\" in extension
            ):
                self.error(f"{label}.agent_extension must be a filename extension")
            if "config" in contract and not self.safe_relative(
                contract.get("config"), f"{label}.config"
            ):
                self.provider_path_preflight_failed = True
            self.provider_contracts[provider_id] = contract

    def validate(self) -> list[str]:
        self.load()
        if not self.spec:
            return self.errors
        self.load_provider_contracts()
        self.validate_shape()
        self.validate_construction_gate()
        self.preflight_provider_paths()
        if self.namespace:
            self.validate_common_files()
            if not self.provider_path_preflight_failed:
                self.validate_adapters()
                self.validate_placeholders()
            self.validate_runtime_markdown_budgets()
        return self.errors

    def validate_provider_path_preflight(self) -> list[str]:
        """Validate spec shape and provider paths without reading adapter artifacts."""
        self.load()
        if not self.spec:
            return self.errors
        self.load_provider_contracts()
        self.validate_shape()
        self.validate_construction_gate()
        self.preflight_provider_paths()
        return self.errors

    def preflight_provider_paths(self) -> None:
        for runtime in sorted(self.runtime_targets):
            contract = self.provider_contracts.get(runtime)
            if not isinstance(contract, dict):
                self.provider_path_preflight_failed = True
                continue
            for key in ("root_guidance", "skill_root", "agent_root", "config"):
                if key in contract:
                    self.provider_path(runtime, key)

    def validate_profile_header(self) -> None:
        value = self.spec.get("profile")
        if self.schema_version == "1.2":
            if not isinstance(value, str) or value not in PROFILES:
                self.error(f"profile must be one of {sorted(PROFILES)} for schema 1.2")
                return
            self.profile = value
        elif "profile" in self.spec:
            self.error("profile is only supported by schema_version '1.2'")

    def validate_receipt_decision(
        self, value: object, label: str, *, allow_null: bool = False
    ) -> tuple[object, str]:
        decision = self.object(value, label)
        self.check_keys(decision, label, {"value", "source"}, {"value", "source"})
        decision_value = decision.get("value")
        if decision_value is None and not allow_null:
            self.error(f"{label}.value must not be null")
        source = decision.get("source")
        if not isinstance(source, str) or source not in DECISION_SOURCES:
            self.error(f"{label}.source must be one of {sorted(DECISION_SOURCES)}")
            source = ""
        elif self.receipt_mode == "create" and source == "approved-decision":
            self.error(f"{label}.source must not be approved-decision in create mode")
        return decision_value, source

    def recommended_profile_for(
        self, current: str | None, reason_codes: set[str]
    ) -> str:
        if reason_codes & GOVERNED_REASON_CODES:
            return "governed"
        if reason_codes & ADAPTIVE_REASON_CODES:
            return "adaptive"
        if reason_codes & LOWER_SCOPE_REASON_CODES and current in PROFILE_RANK:
            target_rank = max(0, PROFILE_RANK[current] - 1)
            return next(
                profile
                for profile, rank in PROFILE_RANK.items()
                if rank == target_rank
            )
        return "core"

    def validate_reporting_receipt(self, value: object) -> None:
        label = "delta-plan.interview_receipt.decisions.reporting"
        reporting = self.object(value, label)
        reporting_keys = {"enabled", "reader_destination", "reader_target"}
        self.check_keys(reporting, label, reporting_keys, reporting_keys)
        enabled, _source = self.validate_receipt_decision(
            reporting.get("enabled"), f"{label}.enabled"
        )
        if not isinstance(enabled, bool):
            self.error(f"{label}.enabled.value must be a boolean")
            enabled = False
        elif enabled is not True:
            self.error(
                f"{label}.enabled.value must be true because reporting policy "
                "is part of the core contract"
            )
        destination_value = reporting.get("reader_destination")
        target_value = reporting.get("reader_target")
        if enabled:
            destination, _ = self.validate_receipt_decision(
                destination_value, f"{label}.reader_destination"
            )
            target, target_source = self.validate_receipt_decision(
                target_value, f"{label}.reader_target"
            )
            if (
                not isinstance(destination, str)
                or destination not in {"file", "notion", "slack"}
            ):
                self.error(
                    f"{label}.reader_destination.value must be file, notion, or slack"
                )
            if not isinstance(target, str) or not target.strip():
                self.error(f"{label}.reader_target.value must be a non-empty string")
            if (
                destination in {"notion", "slack"}
                and target_source not in EXPLICIT_CONFIRMATION_SOURCES
            ):
                self.error(
                    f"{label}.reader_target.source must record explicit user "
                    "confirmation for notion or slack"
                )
            reporting_path = self.harness_root / "policies" / "reporting.json"
            try:
                reporting_policy = json.loads(
                    reporting_path.read_text(encoding="utf-8")
                )
            except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
                self.error(
                    "cannot verify receipt reporting parity against "
                    f"policies/reporting.json: {exc}"
                )
            else:
                if not isinstance(reporting_policy, dict):
                    self.error(
                        "cannot verify receipt reporting parity because "
                        "policies/reporting.json is not an object"
                    )
                else:
                    if destination != reporting_policy.get("reader_destination"):
                        self.error(
                            f"{label}.reader_destination.value must match "
                            "policies/reporting.json"
                        )
                    if target != reporting_policy.get("reader_target"):
                        self.error(
                            f"{label}.reader_target.value must match "
                            "policies/reporting.json"
                        )
        else:
            if destination_value is not None or target_value is not None:
                self.error(
                    f"{label} must use null reader_destination and reader_target "
                    "when reporting is disabled"
                )

    def validate_profile_downgrade_approval(
        self,
        approval_required: list[dict],
        current: str | None,
        selected: str,
    ) -> None:
        entries = [
            entry
            for entry in approval_required
            if entry.get("id") == "profile-downgrade"
        ]
        is_downgrade = (
            current in PROFILE_RANK
            and selected in PROFILE_RANK
            and PROFILE_RANK[selected] < PROFILE_RANK[current]
        )
        if not is_downgrade:
            if entries:
                self.error(
                    "delta-plan approval_required.profile-downgrade is only valid "
                    "for a profile downgrade"
                )
            return
        if len(entries) != 1:
            self.error(
                "profile downgrade requires exactly one approved "
                "approval_required entry with id 'profile-downgrade'"
            )
            return
        entry = entries[0]
        label = "delta-plan.approval_required.profile-downgrade"
        keys = {
            "id",
            "status",
            "from",
            "to",
            "remove_layers",
            "remove_paths",
            "retain_or_archive_paths",
            "confirmation_source",
        }
        self.check_keys(entry, label, keys, keys)
        if entry.get("status") != "approved":
            self.error(f"{label}.status must be 'approved'")
        if entry.get("from") != current or entry.get("to") != selected:
            self.error(f"{label} from/to must match the selected profile transition")
        remove_layers = self.string_list(
            entry.get("remove_layers"), f"{label}.remove_layers"
        )
        expected_layers = {
            profile
            for profile, rank in PROFILE_RANK.items()
            if PROFILE_RANK[selected] < rank <= PROFILE_RANK[current]
        }
        if set(remove_layers) != expected_layers or len(remove_layers) != len(
            set(remove_layers)
        ):
            self.error(
                f"{label}.remove_layers must exactly match "
                f"{sorted(expected_layers)}"
            )
        remove_paths = self.string_list(
            entry.get("remove_paths"), f"{label}.remove_paths"
        )
        if not remove_paths:
            self.error(f"{label}.remove_paths must not be empty")
        for path in remove_paths:
            self.safe_relative(path, f"{label}.remove_paths")
        retained = self.string_list(
            entry.get("retain_or_archive_paths"),
            f"{label}.retain_or_archive_paths",
        )
        for path in retained:
            self.safe_relative(path, f"{label}.retain_or_archive_paths")
        confirmation = entry.get("confirmation_source")
        if (
            not isinstance(confirmation, str)
            or confirmation not in EXPLICIT_CONFIRMATION_SOURCES
        ):
            self.error(
                f"{label}.confirmation_source must record explicit user confirmation"
            )

    def validate_delta_plan(self, path: Path, expected_mode: str | None) -> None:
        label = "delta-plan"
        if not path.is_relative_to(self.target):
            self.error("harness.construction_receipt must stay within the target project")
            return
        if not path.is_file():
            self.error(f"construction receipt is missing: {path}")
            return
        try:
            plan = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            self.error(f"construction receipt is invalid: {exc}")
            return
        plan = self.object(plan, label)
        plan_keys = {
            "schema_version",
            "change_id",
            "mode",
            "interview_receipt",
            "deltas",
            "conflicts",
            "approval_required",
        }
        self.check_keys(plan, label, plan_keys, plan_keys)
        if plan.get("schema_version") != "1.0":
            self.error("delta-plan.schema_version must be '1.0'")
        change_id = self.identifier(plan.get("change_id"), "delta-plan.change_id")
        if change_id:
            expected_path = (
                self.harness_root
                / "maintenance"
                / "runs"
                / change_id
                / "delta-plan.json"
            ).resolve()
            if path != expected_path:
                self.error(
                    "harness.construction_receipt must use canonical path "
                    f"{expected_path!s}"
                )
        mode = plan.get("mode")
        if not isinstance(mode, str) or mode not in CONSTRUCTION_MODES:
            self.error(f"delta-plan.mode must be one of {sorted(CONSTRUCTION_MODES)}")
            self.receipt_mode = ""
        else:
            self.receipt_mode = mode
        if expected_mode is not None and mode != expected_mode:
            self.error(
                "--construction-mode must match delta-plan.mode: "
                f"expected {expected_mode!r}, got {mode!r}"
            )

        receipt = self.object(plan.get("interview_receipt"), "delta-plan.interview_receipt")
        receipt_keys = {"status", "decisions", "profile"}
        self.check_keys(
            receipt,
            "delta-plan.interview_receipt",
            receipt_keys,
            receipt_keys,
        )
        if receipt.get("status") != "complete":
            self.error("delta-plan.interview_receipt.status must be 'complete'")
        decisions = self.object(
            receipt.get("decisions"), "delta-plan.interview_receipt.decisions"
        )
        self.check_keys(
            decisions,
            "delta-plan.interview_receipt.decisions",
            REQUIRED_INTERVIEW_DECISIONS,
            REQUIRED_INTERVIEW_DECISIONS,
        )
        decision_values: dict[str, object] = {}
        decision_sources: dict[str, str] = {}
        for name in sorted(REQUIRED_INTERVIEW_DECISIONS - {"reporting"}):
            decision_values[name], decision_sources[name] = self.validate_receipt_decision(
                decisions.get(name),
                f"delta-plan.interview_receipt.decisions.{name}",
            )
        self.validate_reporting_receipt(decisions.get("reporting"))
        for name in (
            "purpose",
            "deliverable_type",
            "task_evaluator",
            "operation_mode",
            "cost_sensitivity",
            "report_language",
            "terminology",
        ):
            value = decision_values.get(name)
            if not isinstance(value, str) or not value.strip():
                self.error(
                    f"delta-plan interview decision {name!r} must be a "
                    "non-empty string"
                )
        receipt_runtime_targets = self.string_list(
            decision_values.get("runtime_targets"),
            "delta-plan.interview_receipt.decisions.runtime_targets.value",
        )
        if len(receipt_runtime_targets) != len(set(receipt_runtime_targets)):
            self.error(
                "delta-plan interview decision 'runtime_targets' contains duplicates"
            )
        if decision_sources.get("purpose") not in {"request", "user-answer"}:
            self.error(
                "delta-plan interview decision 'purpose' must come from request "
                "or user-answer"
            )

        expected_decisions = {
            "purpose": self.spec.get("harness", {}).get("purpose"),
            "report_language": self.spec.get("communication", {}).get(
                "report_language"
            ),
            "terminology": self.spec.get("communication", {}).get("terminology"),
        }
        for name, expected in expected_decisions.items():
            if decision_values.get(name) != expected:
                self.error(
                    f"delta-plan interview decision {name!r} must match "
                    "the canonical spec"
                )
        if receipt_runtime_targets != self.spec.get("runtime_targets"):
            self.error(
                "delta-plan interview decision 'runtime_targets' must match "
                "the canonical spec"
            )
        task_evaluators = {
            evaluator.get("id")
            for evaluator in self.spec.get("evaluators", [])
            if isinstance(evaluator, dict) and evaluator.get("scope") == "task"
        }
        if decision_values.get("task_evaluator") not in task_evaluators:
            self.error(
                "delta-plan interview decision 'task_evaluator' must reference "
                "a scope=task evaluator"
            )
        entry_skill_id = self.spec.get("orchestration", {}).get("entry_skill")
        entry_skill_evaluator = next(
            (
                skill.get("evaluator")
                for skill in self.spec.get("skills", [])
                if isinstance(skill, dict) and skill.get("id") == entry_skill_id
            ),
            None,
        )
        if decision_values.get("task_evaluator") != entry_skill_evaluator:
            self.error(
                "delta-plan interview decision 'task_evaluator' must match "
                "the entry skill evaluator"
            )
        receipt_gate_ids = self.string_list(
            decision_values.get("approval_gates"),
            "delta-plan.interview_receipt.decisions.approval_gates.value",
        )
        if set(receipt_gate_ids) != self.gate_ids or len(receipt_gate_ids) != len(
            set(receipt_gate_ids)
        ):
            self.error(
                "delta-plan interview decision 'approval_gates' must exactly "
                "match canonical approval gate IDs"
            )

        profile_receipt = self.object(
            receipt.get("profile"), "delta-plan.interview_receipt.profile"
        )
        profile_keys = {
            "current",
            "recommended",
            "selected",
            "reason_codes",
            "confirmation_source",
            "override_reason",
        }
        self.check_keys(
            profile_receipt,
            "delta-plan.interview_receipt.profile",
            profile_keys,
            profile_keys,
        )
        current = profile_receipt.get("current")
        if current is not None and (
            not isinstance(current, str) or current not in PROFILES
        ):
            self.error("delta-plan profile.current must be null or a known profile")
            current = None
        if mode == "create" and current is not None:
            self.error("delta-plan profile.current must be null in create mode")
        recommended = profile_receipt.get("recommended")
        selected = profile_receipt.get("selected")
        if not isinstance(recommended, str) or recommended not in PROFILES:
            self.error("delta-plan profile.recommended must be a known profile")
        if not isinstance(selected, str) or selected not in PROFILES:
            self.error("delta-plan profile.selected must be a known profile")
            selected = ""
        elif selected != self.profile:
            self.error(
                "delta-plan profile.selected must match canonical spec.profile: "
                f"expected {self.profile!r}, got {selected!r}"
            )
        reason_codes = self.string_list(
            profile_receipt.get("reason_codes"),
            "delta-plan.interview_receipt.profile.reason_codes",
        )
        if len(reason_codes) != len(set(reason_codes)):
            self.error("delta-plan profile.reason_codes contains duplicates")
        unknown_reasons = set(reason_codes) - PROFILE_REASON_CODES
        if unknown_reasons:
            self.error(
                f"delta-plan profile.reason_codes contains unknown values: "
                f"{sorted(unknown_reasons)}"
            )
        expected_recommendation = self.recommended_profile_for(
            current, set(reason_codes) - unknown_reasons
        )
        if (
            isinstance(recommended, str)
            and recommended in PROFILES
            and recommended != expected_recommendation
        ):
            self.error(
                "delta-plan profile.recommended does not match reason_codes: "
                f"expected {expected_recommendation!r}, got {recommended!r}"
            )
        if recommended == "core" and not (set(reason_codes) & CORE_REASON_CODES):
            self.error(
                "delta-plan profile.reason_codes must include 'bounded-task-work' "
                "when core is recommended"
            )
        operation_mode = decision_values.get("operation_mode")
        if isinstance(operation_mode, str) and recommended == "core":
            normalized_operation_mode = operation_mode.casefold().replace("_", "-")
            if any(
                marker in normalized_operation_mode
                for marker in (
                    "long-running",
                    "long running",
                    "autonomous",
                    "unattended",
                    "cron",
                    "event",
                )
            ):
                self.error(
                    "long-running or unattended operation_mode cannot recommend core"
                )
        confirmation = profile_receipt.get("confirmation_source")
        if (
            not isinstance(confirmation, str)
            or confirmation not in DECISION_SOURCES
        ):
            self.error(
                "delta-plan profile.confirmation_source must be a known decision source"
            )
        if mode == "create" and confirmation not in {
            "request",
            "user-answer",
            "default-delegated",
        }:
            self.error(
                "delta-plan profile.confirmation_source in create mode must be "
                "request, user-answer, or default-delegated"
            )
        if (
            mode in {"improve", "reconcile"}
            and current is None
            and confirmation not in {"request", "user-answer"}
        ):
            self.error(
                "a legacy profile migration without profile.current requires "
                "request or user-answer confirmation"
            )
        if confirmation == "default-delegated" and selected != recommended:
            self.error(
                "default-delegated profile selection must match the recommendation"
            )
        override_reason = profile_receipt.get("override_reason")
        if selected == recommended:
            if override_reason is not None:
                self.error(
                    "delta-plan profile.override_reason must be null when selected "
                    "matches recommended"
                )
        else:
            if not isinstance(override_reason, str) or not override_reason.strip():
                self.error(
                    "delta-plan profile.override_reason must be a non-empty string "
                    "when selected differs from recommended"
                )
            if (
                not isinstance(confirmation, str)
                or confirmation not in EXPLICIT_CONFIRMATION_SOURCES
            ):
                self.error(
                    "a profile recommendation override requires explicit user confirmation"
                )
        if (
            isinstance(current, str)
            and current in PROFILES
            and isinstance(selected, str)
            and selected in PROFILES
            and current != selected
        ):
            if (
                not isinstance(confirmation, str)
                or confirmation not in EXPLICIT_CONFIRMATION_SOURCES
            ):
                self.error("a profile transition requires explicit user confirmation")

        for field in ("deltas", "conflicts"):
            if not isinstance(plan.get(field), list):
                self.error(f"delta-plan.{field} must be an array")
        approval_required = self.list_of_objects(
            plan.get("approval_required"), "delta-plan.approval_required"
        )
        self.validate_profile_downgrade_approval(
            approval_required, current, selected
        )

    def validate_construction_gate(self) -> None:
        has_mode = self.construction_mode is not None
        has_cli_plan = self.delta_plan_path is not None
        if has_mode != has_cli_plan:
            self.error(
                "--construction-mode and --delta-plan must be supplied together"
            )
            return
        if has_mode and (
            not isinstance(self.construction_mode, str)
            or self.construction_mode not in CONSTRUCTION_MODES
        ):
            self.error(
                f"--construction-mode must be one of {sorted(CONSTRUCTION_MODES)}"
            )
        if self.schema_version != "1.2":
            if has_mode or has_cli_plan:
                self.error(
                    "construction receipt validation is only supported for schema 1.2"
                )
            return
        if self.construction_receipt_path is None:
            return
        if has_cli_plan and self.delta_plan_path != self.construction_receipt_path:
            self.error(
                "--delta-plan must match harness.construction_receipt exactly"
            )
        self.validate_delta_plan(
            self.construction_receipt_path,
            self.construction_mode if has_mode else None,
        )

    def validate_shape(self) -> None:
        version = self.spec.get("schema_version")
        if not isinstance(version, str) or version not in SCHEMA_VERSIONS:
            self.error(f"schema_version must be one of {sorted(SCHEMA_VERSIONS)}")
        else:
            self.schema_version = version
        self.validate_profile_header()
        required_top_level = TOP_LEVEL_KEYS - {
            "self_evaluation",
            "memory",
            "communication",
            "profile",
        }
        if self.schema_version == "1.1":
            required_top_level.update({"self_evaluation", "memory"})
        elif self.schema_version == "1.2":
            required_top_level.update({"profile", "communication"})
            if self.profile in {"adaptive", "governed"}:
                required_top_level.update({"self_evaluation", "memory"})
        self.check_keys(self.spec, "spec", required_top_level, TOP_LEVEL_KEYS)

        harness = self.object(self.spec.get("harness"), "harness")
        harness_keys = {"id", "purpose", "root"}
        if self.schema_version == "1.2":
            harness_keys.add("construction_receipt")
        self.check_keys(
            harness,
            "harness",
            harness_keys,
            harness_keys,
        )
        self.namespace = self.identifier(harness.get("id"), "harness.id")
        purpose = harness.get("purpose")
        if not isinstance(purpose, str) or not purpose.strip():
            self.error("harness.purpose must be a non-empty string")
        root = harness.get("root")
        if self.safe_relative(root, "harness.root"):
            self.harness_root = (self.target / root).resolve()
            if not self.harness_root.is_relative_to(self.target):
                self.error("harness.root escapes the target project")
        if self.schema_version == "1.2":
            receipt = harness.get("construction_receipt")
            if self.safe_relative(receipt, "harness.construction_receipt"):
                resolved_receipt = (self.target / receipt).resolve()
                if not resolved_receipt.is_relative_to(self.target):
                    self.error("harness.construction_receipt escapes the target project")
                else:
                    self.construction_receipt_path = resolved_receipt

        runtimes = self.string_list(self.spec.get("runtime_targets"), "runtime_targets")
        self.runtime_targets = set(runtimes)
        if not runtimes:
            self.error("runtime_targets must not be empty")
        invalid_runtimes = self.runtime_targets - set(self.provider_contracts)
        if invalid_runtimes:
            self.error(f"unsupported runtime targets: {sorted(invalid_runtimes)}")
        if len(runtimes) != len(self.runtime_targets):
            self.error("runtime_targets contains duplicates")

        limits = self.object(self.spec.get("limits"), "limits")
        limit_keys = {
            "max_parallelism",
            "max_delegation_depth",
            "max_instruction_lines",
        }
        required_limit_keys = {"max_parallelism", "max_delegation_depth"}
        if self.schema_version == "1.2":
            limit_keys.update({"target_markdown_lines", "max_markdown_lines"})
            required_limit_keys.update(
                {
                    "max_instruction_lines",
                    "target_markdown_lines",
                    "max_markdown_lines",
                }
            )
        self.check_keys(
            limits,
            "limits",
            required_limit_keys,
            limit_keys,
        )
        for key in ("max_parallelism", "max_delegation_depth"):
            value = limits.get(key)
            if not isinstance(value, int) or isinstance(value, bool) or value < 1:
                self.error(f"limits.{key} must be a positive integer")
        instruction_lines = limits.get("max_instruction_lines")
        if instruction_lines is not None:
            if (
                not isinstance(instruction_lines, int)
                or isinstance(instruction_lines, bool)
                or instruction_lines < 1
            ):
                self.error("limits.max_instruction_lines must be a positive integer")
            else:
                self.max_instruction_lines = instruction_lines
        if self.schema_version == "1.2":
            target_lines = limits.get("target_markdown_lines")
            max_lines = limits.get("max_markdown_lines")
            if (
                not isinstance(target_lines, int)
                or isinstance(target_lines, bool)
                or not 1 <= target_lines <= 50
            ):
                self.error(
                    "limits.target_markdown_lines must be an integer between 1 and 50"
                )
            else:
                self.target_markdown_lines = target_lines
            if (
                not isinstance(max_lines, int)
                or isinstance(max_lines, bool)
                or not 1 <= max_lines <= 100
            ):
                self.error(
                    "limits.max_markdown_lines must be an integer between 1 and 100"
                )
            else:
                self.max_markdown_lines = max_lines
            if (
                self.target_markdown_lines > 0
                and self.max_markdown_lines > 0
                and self.target_markdown_lines > self.max_markdown_lines
            ):
                self.error(
                    "limits.target_markdown_lines must not exceed "
                    "limits.max_markdown_lines"
                )

        communication_value = self.spec.get("communication")
        if communication_value is not None:
            communication = self.object(communication_value, "communication")
            communication_keys = {
                "artifact_language",
                "report_language",
                "terminology",
            }
            self.check_keys(
                communication,
                "communication",
                communication_keys,
                communication_keys,
            )
            if communication.get("artifact_language") != "en":
                self.error("communication.artifact_language must be 'en'")
            report_language = communication.get("report_language")
            if (
                not isinstance(report_language, str)
                or LANGUAGE_TAG_RE.fullmatch(report_language) is None
            ):
                self.error("communication.report_language must be a supported language tag")
            if communication.get("terminology") not in {
                "technical-english",
                "localized",
            }:
                self.error(
                    "communication.terminology must be technical-english or localized"
                )
        if self.requires_english_artifacts():
            self.validate_english_spec_prose()

        domains = self.list_of_objects(self.spec.get("domains"), "domains")
        self.domain_ids = self.unique_ids(domains, "domains")
        agents = self.list_of_objects(self.spec.get("agents"), "agents")
        self.agent_ids = self.unique_ids(agents, "agents")
        skills = self.list_of_objects(self.spec.get("skills"), "skills")
        self.skill_ids = self.unique_ids(skills, "skills")
        evaluators = self.list_of_objects(self.spec.get("evaluators"), "evaluators")
        self.evaluator_ids = self.unique_ids(evaluators, "evaluators")
        approval_gates = self.list_of_objects(
            self.spec.get("approval_gates"), "approval_gates"
        )
        self.gate_ids = self.unique_ids(approval_gates, "approval_gates")

        if not domains:
            self.error("domains must contain at least one domain")
        if len(agents) < 2:
            self.error("agents must contain at least two roles")
        if len(skills) < 3:
            if self.schema_version == "1.2":
                self.error("skills must contain at least three workflow skills")
            else:
                self.error("skills must contain entry, evaluation, and improvement skills")
        if not evaluators:
            self.error("evaluators must contain at least one evaluator")

        for index, domain in enumerate(domains):
            label = f"domains[{index}]"
            self.check_keys(
                domain,
                label,
                {"id", "paths", "coordinator"},
                {"id", "paths", "coordinator"},
            )
            coordinator = domain.get("coordinator")
            if coordinator not in self.agent_ids:
                self.error(f"{label}.coordinator references unknown agent: {coordinator!r}")
            paths = self.string_list(domain.get("paths"), f"{label}.paths")
            if not paths:
                self.error(f"{label}.paths must not be empty")
            for path in paths:
                self.safe_relative(path, f"{label}.paths")

        capabilities: set[str] = set()
        agent_by_id = {agent.get("id"): agent for agent in agents}
        evaluator_by_id = {evaluator.get("id"): evaluator for evaluator in evaluators}
        for index, agent in enumerate(agents):
            label = f"agents[{index}]"
            self.check_keys(
                agent,
                label,
                {
                    "id",
                    "description",
                    "lane",
                    "capabilities",
                    "domains",
                    "model_tier",
                    "access",
                },
                {
                    "id",
                    "description",
                    "lane",
                    "capabilities",
                    "domains",
                    "model_tier",
                    "access",
                },
            )
            lane = agent.get("lane")
            if lane not in LANES:
                self.error(f"{label}.lane must be one of {sorted(LANES)}")
            elif self.profile == "core" and lane == "improvement":
                self.error(f"{label}.lane is forbidden by profile 'core': improvement")
            tier = agent.get("model_tier")
            if tier not in MODEL_TIERS:
                self.error(f"{label}.model_tier must be fast, balanced, or deep")
            if agent.get("access") not in ACCESS:
                self.error(f"{label}.access must be read-only or workspace-write")
            role_capabilities = self.string_list(
                agent.get("capabilities"), f"{label}.capabilities"
            )
            if not role_capabilities:
                self.error(f"{label}.capabilities must not be empty")
            if len(role_capabilities) != len(set(role_capabilities)):
                self.error(f"{label}.capabilities contains duplicates")
            for capability in role_capabilities:
                self.identifier(capability, f"{label}.capabilities")
            capabilities.update(role_capabilities)
            domains_for_agent = self.string_list(agent.get("domains"), f"{label}.domains")
            if len(domains_for_agent) != len(set(domains_for_agent)):
                self.error(f"{label}.domains contains duplicates")
            unknown_domains = set(domains_for_agent) - self.domain_ids
            if unknown_domains:
                self.error(f"{label}.domains references unknown domains: {sorted(unknown_domains)}")
            description = agent.get("description")
            if not isinstance(description, str) or not description.strip():
                self.error(f"{label}.description must be a non-empty string")
        required_capabilities = (
            PROFILE_REQUIRED_CAPABILITIES.get(self.profile, set())
            if self.schema_version == "1.2"
            else REQUIRED_CAPABILITIES
        )
        missing_capabilities = required_capabilities - capabilities
        if missing_capabilities:
            self.error(f"agent capability backbone is incomplete: {sorted(missing_capabilities)}")
        if self.schema_version == "1.2" and self.profile:
            forbidden_capabilities = (
                PROFILE_FORBIDDEN_CAPABILITIES[self.profile] & capabilities
            )
            if forbidden_capabilities:
                self.error(
                    f"profile {self.profile!r} forbids agent capabilities: "
                    f"{sorted(forbidden_capabilities)}"
                )

        skill_kinds: set[str] = set()
        harness_root_value = harness.get("root")
        harness_root_text = (
            harness_root_value.replace("\\", "/").rstrip("/")
            if isinstance(harness_root_value, str)
            else ""
        )
        for index, skill in enumerate(skills):
            label = f"skills[{index}]"
            skill_keys = {
                "id",
                "kind",
                "entry_agent",
                "domains",
                "instructions",
                "evaluator",
            }
            required_skill_keys = skill_keys - {"evaluator"}
            if self.schema_version in {"1.1", "1.2"}:
                required_skill_keys.add("evaluator")
            self.check_keys(skill, label, required_skill_keys, skill_keys)
            kind = skill.get("kind")
            if not isinstance(kind, str) or kind not in {
                "entry",
                "evaluation",
                "verification",
                "harness-evaluation",
                "improvement",
                "domain",
            }:
                self.error(f"{label}.kind is invalid: {kind!r}")
            else:
                skill_kinds.add(kind)
            if skill.get("entry_agent") not in self.agent_ids:
                self.error(f"{label}.entry_agent references unknown agent")
            skill_domains = self.string_list(skill.get("domains"), f"{label}.domains")
            if len(skill_domains) != len(set(skill_domains)):
                self.error(f"{label}.domains contains duplicates")
            unknown_domains = set(skill_domains) - self.domain_ids
            if unknown_domains:
                self.error(f"{label}.domains references unknown domains: {sorted(unknown_domains)}")
            instructions = skill.get("instructions")
            if self.safe_relative(instructions, f"{label}.instructions"):
                expected = f"{harness_root_text}/skills/{skill.get('id')}/SKILL.md"
                if instructions.replace("\\", "/") != expected:
                    self.error(
                        f"{label}.instructions must use canonical path {expected!r}"
                    )
            evaluator_id = skill.get("evaluator")
            if evaluator_id is not None:
                if evaluator_id not in self.evaluator_ids:
                    self.error(f"{label}.evaluator references unknown evaluator")
                elif kind in TASK_SKILL_KINDS | HARNESS_SKILL_KINDS:
                    expected_scope = "harness" if kind in HARNESS_SKILL_KINDS else "task"
                    actual_scope = evaluator_by_id[evaluator_id].get("scope")
                    if actual_scope is not None and actual_scope != expected_scope:
                        self.error(
                            f"{label}.evaluator must have {expected_scope} scope"
                        )
        if self.schema_version == "1.2":
            required_skill_kinds = PROFILE_REQUIRED_SKILL_KINDS.get(
                self.profile, set()
            )
        else:
            required_skill_kinds = {"entry", "evaluation", "improvement"}
            if self.schema_version == "1.1":
                required_skill_kinds.update({"harness-evaluation", "verification"})
        for required_kind in required_skill_kinds:
            if required_kind not in skill_kinds:
                self.error(f"skills is missing kind: {required_kind}")
        if self.schema_version == "1.2" and self.profile:
            forbidden_skill_kinds = (
                PROFILE_FORBIDDEN_SKILL_KINDS[self.profile] & skill_kinds
            )
            if forbidden_skill_kinds:
                self.error(
                    f"profile {self.profile!r} forbids skill kinds: "
                    f"{sorted(forbidden_skill_kinds)}"
                )

        orchestration = self.object(self.spec.get("orchestration"), "orchestration")
        self.check_keys(
            orchestration,
            "orchestration",
            {"entry_skill", "handoffs"},
            {"entry_skill", "handoffs"},
        )
        entry_skill = orchestration.get("entry_skill")
        if entry_skill not in self.skill_ids:
            self.error("orchestration.entry_skill references an unknown skill")
        else:
            matching = [s for s in skills if s.get("id") == entry_skill]
            if matching and matching[0].get("kind") != "entry":
                self.error("orchestration.entry_skill must reference an entry skill")
        handoffs = self.list_of_objects(
            orchestration.get("handoffs"), "orchestration.handoffs"
        )
        if not handoffs:
            self.error("orchestration.handoffs must contain at least one handoff")
        edges: list[tuple[str, str]] = []
        for index, handoff in enumerate(handoffs):
            label = f"orchestration.handoffs[{index}]"
            self.check_keys(
                handoff,
                label,
                {"from", "to", "when", "artifacts"},
                {"from", "to", "when", "artifacts"},
            )
            source = handoff.get("from")
            destination = handoff.get("to")
            if source not in self.agent_ids:
                self.error(f"{label}.from references unknown agent: {source!r}")
            if destination not in self.agent_ids:
                self.error(f"{label}.to references unknown agent: {destination!r}")
            if source in self.agent_ids and destination in self.agent_ids:
                edges.append((source, destination))
            when = handoff.get("when")
            if not isinstance(when, str) or not when.strip():
                self.error(f"{label}.when must be a non-empty string")
            for artifact in self.string_list(handoff.get("artifacts"), f"{label}.artifacts"):
                self.safe_relative(artifact, f"{label}.artifacts")
        self.validate_dag(edges)

        evaluator_scopes: set[str] = set()
        evaluator_keys = {
            "id",
            "scope",
            "owner",
            "runner",
            "type",
            "command",
            "pass_condition",
        }
        for index, evaluator in enumerate(evaluators):
            label = f"evaluators[{index}]"
            required_evaluator_keys = evaluator_keys - {"scope"}
            if self.schema_version in {"1.1", "1.2"}:
                required_evaluator_keys.add("scope")
            self.check_keys(
                evaluator,
                label,
                required_evaluator_keys,
                evaluator_keys,
            )
            scope = evaluator.get("scope")
            if scope is not None:
                if not isinstance(scope, str) or scope not in {"task", "harness"}:
                    self.error(f"{label}.scope must be task or harness")
                else:
                    evaluator_scopes.add(scope)
            owner = evaluator.get("owner")
            runner = evaluator.get("runner")
            if owner not in self.agent_ids:
                self.error(f"{label}.owner references unknown agent")
            if runner not in self.agent_ids:
                self.error(f"{label}.runner references unknown agent")
            if owner in agent_by_id and "verdict" not in agent_by_id[owner].get("capabilities", []):
                self.error(f"{label}.owner lacks verdict capability")
            if runner in agent_by_id and "verification" not in agent_by_id[runner].get("capabilities", []):
                self.error(f"{label}.runner lacks verification capability")
            if evaluator.get("type") not in {
                "deterministic",
                "manual-deterministic",
                "rubric",
                "experiment",
            }:
                self.error(f"{label}.type is invalid")
            for field in ("command", "pass_condition"):
                value = evaluator.get(field)
                if not isinstance(value, str) or not value.strip():
                    self.error(f"{label}.{field} must be a non-empty string")
        if self.schema_version == "1.1":
            required_evaluator_scopes = {"task", "harness"}
        elif self.schema_version == "1.2":
            required_evaluator_scopes = PROFILE_REQUIRED_EVALUATOR_SCOPES.get(
                self.profile, set()
            )
        else:
            required_evaluator_scopes = set()
        for required_scope in required_evaluator_scopes:
            if required_scope not in evaluator_scopes:
                self.error(f"evaluators is missing scope: {required_scope}")
        if self.schema_version == "1.2" and self.profile:
            forbidden_evaluator_scopes = (
                PROFILE_FORBIDDEN_EVALUATOR_SCOPES[self.profile] & evaluator_scopes
            )
            if forbidden_evaluator_scopes:
                self.error(
                    f"profile {self.profile!r} forbids evaluator scopes: "
                    f"{sorted(forbidden_evaluator_scopes)}"
                )

        for index, gate in enumerate(approval_gates):
            label = f"approval_gates[{index}]"
            self.check_keys(
                gate,
                label,
                {"id", "trigger", "owner", "required_action"},
                {"id", "trigger", "owner", "required_action"},
            )
            self.identifier(gate.get("owner"), f"{label}.owner")
            for field in ("trigger", "required_action"):
                value = gate.get(field)
                if not isinstance(value, str) or not value.strip():
                    self.error(f"{label}.{field} must be a non-empty string")

        memory_value = self.spec.get("memory")
        if self.profile == "core" and memory_value is not None:
            self.error("profile 'core' forbids memory")
        if memory_value is not None:
            memory = self.object(memory_value, "memory")
            self.check_keys(
                memory,
                "memory",
                {"index", "policy", "max_document_lines"},
                {"index", "policy", "max_document_lines", "max_summary_chars"},
            )
            index_path = memory.get("index")
            if self.safe_relative(index_path, "memory.index"):
                expected_index = f"{harness_root_text}/memory/INDEX.md"
                if index_path.replace("\\", "/") != expected_index:
                    self.error(f"memory.index must use canonical path {expected_index!r}")
                resolved_index = (self.target / index_path).resolve()
                if not resolved_index.is_relative_to(self.target):
                    self.error("memory.index escapes the target project")
                else:
                    self.memory_index_path = resolved_index
            if memory.get("policy") != "preserve-and-reconcile":
                self.error("memory.policy must be 'preserve-and-reconcile'")
            max_lines = memory.get("max_document_lines")
            if not isinstance(max_lines, int) or isinstance(max_lines, bool) or max_lines < 1:
                self.error("memory.max_document_lines must be a positive integer")
            else:
                self.memory_max_document_lines = max_lines
            max_summary_chars = memory.get("max_summary_chars")
            if max_summary_chars is not None:
                if (
                    not isinstance(max_summary_chars, int)
                    or isinstance(max_summary_chars, bool)
                    or max_summary_chars < 1
                ):
                    self.error("memory.max_summary_chars must be a positive integer")
                else:
                    self.memory_max_summary_chars = max_summary_chars

        loops = self.object(self.spec.get("loops"), "loops")
        loop_keys = {
            "execution",
            "evaluation",
            "improvement",
            "improvement_owner",
            "fail_threshold",
            "retro_interval",
        }
        if self.schema_version == "1.2":
            required_loop_keys = PROFILE_REQUIRED_LOOP_KEYS.get(self.profile, set())
        else:
            required_loop_keys = loop_keys - {"retro_interval"}
            if self.schema_version == "1.0":
                required_loop_keys.add("retro_interval")
        self.check_keys(loops, "loops", required_loop_keys, loop_keys)
        if self.schema_version == "1.2" and self.profile:
            forbidden_loop_keys = PROFILE_FORBIDDEN_LOOP_KEYS[self.profile] & set(
                loops
            )
            if forbidden_loop_keys:
                self.error(
                    f"profile {self.profile!r} forbids loop keys: "
                    f"{sorted(forbidden_loop_keys)}"
                )
        if "improvement_owner" in loops:
            improvement_owner = loops.get("improvement_owner")
            if improvement_owner not in self.agent_ids:
                self.error("loops.improvement_owner references unknown agent")
            elif "improvement" not in agent_by_id[improvement_owner].get(
                "capabilities", []
            ):
                self.error("loops.improvement_owner lacks improvement capability")
        for field in ("execution", "evaluation", "improvement"):
            if field in loops:
                self.safe_relative(loops.get(field), f"loops.{field}")
        fail_threshold = loops.get("fail_threshold")
        if (
            not isinstance(fail_threshold, int)
            or isinstance(fail_threshold, bool)
            or fail_threshold < 1
        ):
            self.error("loops.fail_threshold must be a positive integer")
        if "retro_interval" in loops:
            retro_interval = loops.get("retro_interval")
            if (
                not isinstance(retro_interval, int)
                or isinstance(retro_interval, bool)
                or retro_interval < 1
            ):
                self.error("loops.retro_interval must be a positive integer")

        if self.profile == "core" and "self_evaluation" in self.spec:
            self.error("profile 'core' forbids self_evaluation")
        if (
            self.schema_version == "1.1"
            or self.profile in {"adaptive", "governed"}
            or "self_evaluation" in self.spec
        ):
            self.validate_self_evaluation()

    def validate_self_evaluation(self) -> None:
        policy = self.object(self.spec.get("self_evaluation"), "self_evaluation")
        policy_keys = {
            "mode",
            "checker",
            "state",
            "evaluation_loop",
            "evaluator",
            "watched_paths",
            "targeted_suite",
            "targeted_sample_rate",
            "full_interval_units",
            "cooldown_units",
            "budget_ratio",
            "success_rate_drop_points",
            "cost_increase_ratio",
            "retry_threshold",
            "minimum_samples",
            "mandatory_events",
        }
        self.check_keys(policy, "self_evaluation", policy_keys, policy_keys)
        if policy.get("mode") != "event-driven":
            self.error("self_evaluation.mode must be 'event-driven'")

        harness_root = self.harness_root_text()
        expected_paths = {
            "checker": f"{harness_root}/triggers/check_self_evaluation.py",
            "state": f"{harness_root}/state/self-evaluation.json",
            "evaluation_loop": f"{harness_root}/loops/HARNESS-EVAL-LOOP.md",
            "targeted_suite": f"{harness_root}/evaluation/suites/targeted.json",
        }
        for field, expected in expected_paths.items():
            value = policy.get(field)
            if self.safe_relative(value, f"self_evaluation.{field}"):
                normalized = value.replace("\\", "/")
                if harness_root and normalized != expected:
                    self.error(
                        f"self_evaluation.{field} must use canonical path {expected!r}"
                    )

        evaluator_id = policy.get("evaluator")
        if not isinstance(evaluator_id, str) or evaluator_id not in self.evaluator_ids:
            self.error("self_evaluation.evaluator references an unknown evaluator")
        else:
            evaluators = {
                evaluator.get("id"): evaluator
                for evaluator in self.spec.get("evaluators", [])
                if isinstance(evaluator, dict)
            }
            if evaluators[evaluator_id].get("scope") != "harness":
                self.error("self_evaluation.evaluator must have harness scope")
            if evaluators[evaluator_id].get("type") != "experiment":
                self.error("self_evaluation.evaluator must have experiment type")

        if (
            self.schema_version in {"1.1", "1.2"}
            and self.profile != "core"
            and isinstance(evaluator_id, str)
        ):
            for index, skill in enumerate(self.spec.get("skills", [])):
                if not isinstance(skill, dict) or skill.get("kind") not in HARNESS_SKILL_KINDS:
                    continue
                if skill.get("evaluator") != evaluator_id:
                    self.error(
                        f"skills[{index}].evaluator must match "
                        f"self_evaluation.evaluator {evaluator_id!r} for "
                        f"kind {skill.get('kind')!r}"
                    )

        watched_paths = self.string_list(
            policy.get("watched_paths"), "self_evaluation.watched_paths"
        )
        if not watched_paths:
            self.error("self_evaluation.watched_paths must not be empty")
        if len(watched_paths) != len(set(watched_paths)):
            self.error("self_evaluation.watched_paths contains duplicates")
        for watched_path in watched_paths:
            self.safe_relative(watched_path, "self_evaluation.watched_paths")
        expected_watched_paths: set[str] = set()
        for runtime in self.runtime_targets:
            contract = self.provider_contracts.get(runtime, {})
            root_guidance = contract.get("root_guidance")
            if isinstance(root_guidance, str):
                expected_watched_paths.add(root_guidance.replace("\\", "/"))
            config = contract.get("config")
            if isinstance(config, str):
                expected_watched_paths.add(config.replace("\\", "/"))
            skill_root = contract.get("skill_root")
            if isinstance(skill_root, str):
                normalized_skill_root = skill_root.replace(chr(92), "/").rstrip("/")
                for skill_id in self.skill_ids:
                    expected_watched_paths.add(
                        f"{normalized_skill_root}/{skill_id}/SKILL.md"
                    )
            agent_root = contract.get("agent_root")
            extension = contract.get("agent_extension")
            if isinstance(agent_root, str) and isinstance(extension, str):
                normalized_agent_root = agent_root.replace(chr(92), "/").rstrip("/")
                for role in self.agent_ids:
                    expected_watched_paths.add(
                        f"{normalized_agent_root}/{self.namespace}-{role}{extension}"
                    )
        normalized_watched_paths = {path.replace("\\", "/") for path in watched_paths}
        if normalized_watched_paths != expected_watched_paths:
            self.error(
                "self_evaluation.watched_paths must exactly match selected provider "
                f"artifacts: expected {sorted(expected_watched_paths)}, got "
                f"{sorted(normalized_watched_paths)}"
            )

        self.number_in_range(
            policy.get("targeted_sample_rate"),
            "self_evaluation.targeted_sample_rate",
            minimum=0,
            maximum=1,
        )
        self.number_in_range(
            policy.get("budget_ratio"),
            "self_evaluation.budget_ratio",
            minimum=0,
            maximum=1,
            exclusive_minimum=True,
        )
        self.number_in_range(
            policy.get("success_rate_drop_points"),
            "self_evaluation.success_rate_drop_points",
            minimum=0,
            maximum=100,
            exclusive_minimum=True,
        )
        self.number_in_range(
            policy.get("cost_increase_ratio"),
            "self_evaluation.cost_increase_ratio",
            minimum=0,
        )
        for field in ("full_interval_units", "retry_threshold", "minimum_samples"):
            self.number_in_range(
                policy.get(field),
                f"self_evaluation.{field}",
                minimum=1,
                integer=True,
            )
        self.number_in_range(
            policy.get("cooldown_units"),
            "self_evaluation.cooldown_units",
            minimum=0,
            integer=True,
        )

        mandatory_events = self.string_list(
            policy.get("mandatory_events"), "self_evaluation.mandatory_events"
        )
        if not mandatory_events:
            self.error("self_evaluation.mandatory_events must not be empty")
        if len(mandatory_events) != len(set(mandatory_events)):
            self.error("self_evaluation.mandatory_events contains duplicates")
        for event in mandatory_events:
            self.identifier(event, "self_evaluation.mandatory_events")
        missing_mandatory_events = (
            MANDATORY_SELF_EVALUATION_EVENTS - set(mandatory_events)
        )
        if missing_mandatory_events:
            self.error(
                "self_evaluation.mandatory_events is missing required events: "
                f"{sorted(missing_mandatory_events)}"
            )

    def validate_dag(self, edges: list[tuple[str, str]]) -> None:
        graph = {role: [] for role in self.agent_ids}
        indegree = {role: 0 for role in self.agent_ids}
        for source, destination in edges:
            graph[source].append(destination)
            indegree[destination] += 1
        queue = [role for role, degree in indegree.items() if degree == 0]
        visited = 0
        while queue:
            role = queue.pop()
            visited += 1
            for destination in graph[role]:
                indegree[destination] -= 1
                if indegree[destination] == 0:
                    queue.append(destination)
        if visited != len(self.agent_ids):
            self.error("orchestration.handoffs must be acyclic; improvement feedback belongs in loops")

    def required_common_files(self) -> set[str]:
        if self.schema_version == "1.2" and self.profile:
            return set(PROFILE_REQUIRED_COMMON_FILES[self.profile])
        required = {
            "HARNESS.md",
            "harness-spec.json",
            "ENVIRONMENT.md",
            "team/TEAM-ARCHITECTURE.md",
            "loops/EXECUTION-LOOP.md",
            "loops/EVAL-LOOP.md",
            "loops/IMPROVE-LOOP.md",
            "recovery/RECOVERY-PLAYBOOK.md",
            "recovery/CHECKPOINT.md",
            "ledger/JOURNAL-FORMAT.md",
            "ledger/DECISIONS.md",
            "ledger/journal.jsonl",
            "budget/CONTEXT-BUDGET.md",
            "state/state.json",
        }
        if self.schema_version == "1.1" or "memory" in self.spec:
            required.add("memory/INDEX.md")
        if self.schema_version == "1.1" or "self_evaluation" in self.spec:
            required.update(
                {
                    "loops/HARNESS-EVAL-LOOP.md",
                    "evaluation/EVALUATION-CONTRACT.md",
                    "evaluation/suites/targeted.json",
                    "triggers/check_self_evaluation.py",
                    "triggers/record_self_evaluation.py",
                    "state/self-evaluation.json",
                }
            )
        return required

    def validate_common_files(self) -> None:
        for relative in sorted(self.required_common_files()):
            if not (self.harness_root / relative).is_file():
                self.error(f"missing common harness file: {relative}")
        if self.schema_version == "1.2" and self.profile:
            for relative in sorted(PROFILE_FORBIDDEN_COMMON_FILES[self.profile]):
                if (self.harness_root / relative).exists():
                    self.error(
                        f"profile {self.profile!r} forbids active common harness file: "
                        f"{relative}"
                    )
        common_agents = self.harness_root / "team" / "agents"
        actual = {path.stem for path in common_agents.glob("*.md")} if common_agents.is_dir() else set()
        if actual != self.agent_ids:
            self.error(
                f"common agent parity mismatch: expected {sorted(self.agent_ids)}, got {sorted(actual)}"
            )
        agents_by_id = {agent["id"]: agent for agent in self.spec.get("agents", [])}
        for role in self.agent_ids:
            path = common_agents / f"{role}.md"
            if not path.is_file():
                continue
            fields = self.frontmatter_fields(path)
            self.check_keys(
                fields,
                f"common agent {path.name} frontmatter",
                {"id", "lane", "model-tier", "access"},
                {"id", "lane", "model-tier", "access"},
            )
            expected_fields = {
                "id": role,
                "lane": agents_by_id[role].get("lane"),
                "model-tier": agents_by_id[role].get("model_tier"),
                "access": agents_by_id[role].get("access"),
            }
            for key, expected in expected_fields.items():
                if fields.get(key) != expected:
                    self.error(
                        f"common agent {key} parity mismatch: {path.name}"
                    )
            description = agents_by_id[role].get("description")
            if isinstance(description, str) and description not in path.read_text(
                encoding="utf-8"
            ):
                self.error(f"common agent description parity mismatch: {path.name}")
        for skill in self.spec.get("skills", []):
            skill_id = skill.get("id")
            canonical = self.canonical_skill_path(skill)
            if canonical is None:
                continue
            if not canonical.is_file():
                self.error(f"missing canonical skill instructions: {skill_id}")
                continue
            if self.frontmatter_name(canonical) != skill_id:
                self.error(f"canonical skill frontmatter name mismatch: {skill_id}")
            self.check_keys(
                self.frontmatter_fields(canonical),
                f"canonical skill {skill_id} frontmatter",
                {"name", "description"},
                {"name", "description"},
            )
            self.require_json_frontmatter_string(
                canonical, "description", f"canonical skill {skill_id}"
            )
            spec_reference = f"{self.harness_root_text()}/harness-spec.json"
            canonical_text = canonical.read_text(encoding="utf-8")
            if spec_reference not in canonical_text:
                self.error(f"canonical skill does not reference common spec: {skill_id}")
            if self.max_instruction_lines > 0:
                line_count = len(canonical_text.splitlines())
                if (
                    line_count > self.max_instruction_lines
                    and "<!-- instruction-budget exception:" not in canonical_text
                ):
                    self.error(
                        f"canonical skill exceeds limits.max_instruction_lines "
                        f"({line_count} > {self.max_instruction_lines}): {skill_id}"
                    )
        state_path = self.harness_root / "state" / "state.json"
        if state_path.is_file():
            try:
                state = json.loads(state_path.read_text(encoding="utf-8"))
                if not state.get("next_action"):
                    self.error("state.next_action must not be empty")
                queue = state.get("queue")
                if not isinstance(queue, list) or not queue:
                    self.error("state.queue must contain at least one work unit")
                else:
                    for index, unit in enumerate(queue):
                        if not isinstance(unit, dict):
                            self.error(f"state.queue[{index}] must be an object")
                            continue
                        evaluator = unit.get("evaluator")
                        if not evaluator:
                            self.error(f"state.queue[{index}] is missing evaluator")
                        elif evaluator not in self.evaluator_ids:
                            self.error(
                                f"state.queue[{index}] references unknown evaluator: "
                                f"{evaluator!r}"
                            )
            except (OSError, json.JSONDecodeError) as exc:
                self.error(f"state/state.json is invalid: {exc}")
        journal = self.harness_root / "ledger" / "journal.jsonl"
        if journal.is_file() and not journal.read_text(encoding="utf-8").strip():
            self.error("ledger/journal.jsonl must contain an initial event")
        self.validate_reporting_policy()
        if self.profile == "governed":
            self.validate_learning_gate_policy()
        if self.memory_index_path is not None and self.memory_index_path.is_file():
            self.validate_memory_index(self.memory_index_path)
        if self.requires_english_artifacts():
            self.validate_english_artifact_markdown()
        if "self_evaluation" in self.spec:
            self.validate_targeted_suite()
            self.validate_self_evaluation_state()

    def validate_reporting_policy(self) -> None:
        """Validate the optional reader-facing reporting policy.

        Existing harnesses predate this policy, so its absence is valid. Once a
        policy is present, however, its destination and canonical-evidence
        contract must be deterministic.
        """
        path = self.harness_root / "policies" / "reporting.json"
        if not path.exists():
            return
        if not path.is_file():
            self.error("policies/reporting.json must be a file when present")
            return
        try:
            policy = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            self.error(f"policies/reporting.json is invalid: {exc}")
            return
        if not isinstance(policy, dict):
            self.error("policies/reporting.json must be a JSON object")
            return
        required = {
            "schema_version",
            "reader_destination",
            "reader_target",
            "canonical_evidence",
            "style",
        }
        self.check_keys(policy, "policies/reporting.json", required, required)
        if policy.get("schema_version") != "1.0":
            self.error("policies/reporting.json.schema_version must be '1.0'")
        destination = policy.get("reader_destination")
        if destination not in {"file", "notion", "slack"}:
            self.error(
                "policies/reporting.json.reader_destination must be file, notion, or slack"
            )
        target = policy.get("reader_target")
        if not isinstance(target, str) or not target.strip():
            self.error("policies/reporting.json.reader_target must be a non-empty string")
        elif destination == "file":
            if target.replace("\\", "/").rstrip("/") != "harness/reports":
                self.error(
                    "policies/reporting.json.reader_target must be 'harness/reports' for file"
                )
            self.safe_relative(target, "policies/reporting.json.reader_target")
        if policy.get("canonical_evidence") != "file":
            self.error("policies/reporting.json.canonical_evidence must be 'file'")
        if policy.get("style") != "plain-language-first":
            self.error(
                "policies/reporting.json.style must be 'plain-language-first'"
            )

    def validate_learning_gate_policy(self) -> None:
        path = self.harness_root / "policies" / "learning-gate.json"
        if not path.is_file():
            return
        try:
            policy = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            self.error(f"policies/learning-gate.json is invalid: {exc}")
            return
        policy = self.object(policy, "policies/learning-gate.json")
        if policy.get("schema_version") != "1.0":
            self.error("policies/learning-gate.json.schema_version must be '1.0'")
        if not isinstance(policy.get("enabled"), bool):
            self.error("policies/learning-gate.json.enabled must be a boolean")
        control = self.object(
            policy.get("control"), "policies/learning-gate.json.control"
        )
        if control.get("owner") != "user":
            self.error("policies/learning-gate.json.control.owner must be 'user'")
        if control.get("agents_may_change_enabled") is not False:
            self.error(
                "policies/learning-gate.json.control.agents_may_change_enabled "
                "must be false"
            )
        if control.get("activation_requires_explicit_user_instruction") is not True:
            self.error(
                "policies/learning-gate.json.control."
                "activation_requires_explicit_user_instruction must be true"
            )

    def requires_english_artifacts(self) -> bool:
        communication = self.spec.get("communication")
        return (
            isinstance(communication, dict)
            and communication.get("artifact_language") == "en"
        )

    def validate_english_spec_prose(self) -> None:
        """Reject non-English prose in effect-bearing canonical spec fields."""
        fields: list[tuple[str, object]] = []
        harness = self.spec.get("harness")
        if isinstance(harness, dict):
            fields.append(("harness.purpose", harness.get("purpose")))

        for collection_name, field_names in (
            ("agents", ("description",)),
            ("evaluators", ("pass_condition",)),
            ("approval_gates", ("trigger", "required_action")),
        ):
            collection = self.spec.get(collection_name)
            if not isinstance(collection, list):
                continue
            for index, item in enumerate(collection):
                if not isinstance(item, dict):
                    continue
                for field_name in field_names:
                    fields.append(
                        (
                            f"{collection_name}[{index}].{field_name}",
                            item.get(field_name),
                        )
                    )

        orchestration = self.spec.get("orchestration")
        if isinstance(orchestration, dict):
            handoffs = orchestration.get("handoffs")
            if isinstance(handoffs, list):
                for index, handoff in enumerate(handoffs):
                    if isinstance(handoff, dict):
                        fields.append(
                            (
                                f"orchestration.handoffs[{index}].when",
                                handoff.get("when"),
                            )
                        )

        for label, value in fields:
            if not isinstance(value, str):
                continue
            letters = non_english_letters(value)
            if letters:
                self.error(
                    "canonical spec prose must be English when "
                    "communication.artifact_language is 'en': "
                    f"{label} contains {letters!r}"
                )

    def validate_english_artifact_markdown(self) -> None:
        """Reject non-English prose in new canonical Markdown artifacts."""
        for path in sorted(self.harness_root.rglob("*.md")):
            relative = path.relative_to(self.harness_root).as_posix()
            if relative == "memory/INDEX.md" or relative.startswith(
                (
                    "evaluation/baselines/",
                    "evaluation/reports/",
                    "evaluation/runs/",
                )
            ):
                continue
            try:
                lines = path.read_text(encoding="utf-8").splitlines()
            except (OSError, UnicodeDecodeError) as exc:
                self.error(f"cannot inspect canonical prose in {relative}: {exc}")
                continue
            fence_marker = ""
            for line_number, line in enumerate(lines, start=1):
                fence = FENCE_RE.match(line)
                if fence_marker:
                    if (
                        fence
                        and fence.group(1)[0] == fence_marker[0]
                        and len(fence.group(1)) >= len(fence_marker)
                    ):
                        fence_marker = ""
                    continue
                if fence:
                    fence_marker = fence.group(1)
                    continue
                letters = non_english_letters(line)
                if letters:
                    self.error(
                        "canonical prose must be English when "
                        "communication.artifact_language is 'en': "
                        f"{relative}:{line_number} contains {letters!r}"
                    )

    def document_exception_position(
        self, lines: list[str], *, managed_block: bool
    ) -> int | None:
        start = 1 if managed_block and lines else 0
        first = next(
            (index for index in range(start, len(lines)) if lines[index].strip()),
            None,
        )
        if first is None:
            return None
        if not managed_block and lines[first].strip() == "---":
            closing = next(
                (
                    index
                    for index in range(first + 1, len(lines))
                    if lines[index].strip() == "---"
                ),
                None,
            )
            if closing is not None:
                return next(
                    (
                        index
                        for index in range(closing + 1, len(lines))
                        if lines[index].strip()
                    ),
                    None,
                )
        return first

    def validate_document_budget_exception(
        self, lines: list[str], label: str, *, managed_block: bool
    ) -> None:
        marker_indexes = [
            index
            for index, line in enumerate(lines)
            if "document-budget exception:" in line
        ]
        if len(marker_indexes) != 1:
            self.error(
                f"runtime-facing Markdown over its target requires exactly one "
                f"document-budget exception: {label}"
            )
            return
        marker_index = marker_indexes[0]
        marker = lines[marker_index]
        match = DOCUMENT_BUDGET_EXCEPTION_RE.fullmatch(marker)
        if match is None:
            self.error(
                "document-budget exception must use exact syntax "
                "'<!-- document-budget exception: <type> | <reason> -->': "
                f"{label}"
            )
            return
        exception_type = match.group("type")
        if exception_type not in DOCUMENT_BUDGET_EXCEPTION_TYPES:
            self.error(
                f"document-budget exception type is invalid in {label}: "
                f"{exception_type!r}"
            )
        reason = match.group("reason").strip()
        normalized_reason = reason.casefold()
        if (
            not reason
            or reason != match.group("reason")
            or any(token in reason for token in ("{{", "}}"))
            or normalized_reason
            in {"reason", "<reason>", "todo", "tbd", "placeholder"}
        ):
            self.error(
                f"document-budget exception reason must be concrete in {label}"
            )
        expected_index = self.document_exception_position(
            lines, managed_block=managed_block
        )
        if marker_index != expected_index:
            location = (
                "immediately after the managed-block start marker"
                if managed_block
                else "at the first nonblank line after optional YAML frontmatter"
            )
            self.error(
                f"document-budget exception must appear {location}: {label}"
            )

    def validate_runtime_markdown_lines(
        self, lines: list[str], label: str, *, managed_block: bool = False
    ) -> None:
        if self.target_markdown_lines < 1 or self.max_markdown_lines < 1:
            return
        line_count = len(lines)
        if line_count > self.max_markdown_lines:
            self.error(
                "runtime-facing Markdown exceeds limits.max_markdown_lines "
                f"({line_count} > {self.max_markdown_lines}): {label}"
            )
            return
        has_marker = any("document-budget exception:" in line for line in lines)
        if line_count <= self.target_markdown_lines:
            if has_marker:
                self.error(
                    "document-budget exception is stale at or below "
                    f"limits.target_markdown_lines: {label}"
                )
            return
        if line_count > self.target_markdown_lines:
            self.validate_document_budget_exception(
                lines, label, managed_block=managed_block
            )

    def canonical_runtime_markdown_paths(self) -> set[Path]:
        paths: set[Path] = set()
        for relative in ("HARNESS.md", "ENVIRONMENT.md"):
            path = self.harness_root / relative
            if path.is_file():
                paths.add(path)
        for relative in (
            "team",
            "skills",
            "loops",
            "policies",
            "memory",
            "recovery",
            "budget",
        ):
            root = self.harness_root / relative
            if root.is_dir():
                paths.update(path for path in root.rglob("*.md") if path.is_file())
        for relative in ("evaluation", "maintenance"):
            root = self.harness_root / relative
            if root.is_dir():
                paths.update(path for path in root.glob("*.md") if path.is_file())
        journal_format = self.harness_root / "ledger" / "JOURNAL-FORMAT.md"
        if journal_format.is_file():
            paths.add(journal_format)
        return paths

    def managed_root_guidance_lines(self, runtime: str) -> tuple[list[str], str] | None:
        path = self.provider_path(runtime, "root_guidance")
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except (OSError, UnicodeDecodeError):
            return None
        start_marker = f"<!-- harness-factory:start {self.namespace} -->"
        end_marker = f"<!-- harness-factory:end {self.namespace} -->"
        starts = [index for index, line in enumerate(lines) if line == start_marker]
        ends = [index for index, line in enumerate(lines) if line == end_marker]
        if len(starts) != 1 or len(ends) != 1 or ends[0] <= starts[0]:
            self.error(
                f"{path.name} must contain one ordered managed block for "
                f"{self.namespace!r}"
            )
            return None
        label = path.relative_to(self.target).as_posix()
        return lines[starts[0] : ends[0] + 1], label

    def validate_runtime_markdown_budgets(self) -> None:
        if self.schema_version != "1.2":
            return
        for path in sorted(self.canonical_runtime_markdown_paths()):
            label = path.relative_to(self.target).as_posix()
            try:
                lines = path.read_text(encoding="utf-8").splitlines()
            except (OSError, UnicodeDecodeError) as exc:
                self.error(f"cannot inspect runtime-facing Markdown {label}: {exc}")
                continue
            self.validate_runtime_markdown_lines(lines, label)
        if self.provider_path_preflight_failed:
            return
        for runtime in sorted(self.runtime_targets):
            contract = self.provider_contracts.get(runtime, {})
            if contract.get("agent_extension") == ".md":
                agent_root = self.provider_path(runtime, "agent_root")
                for role in sorted(self.agent_ids):
                    path = agent_root / f"{self.namespace}-{role}.md"
                    if not path.is_file():
                        continue
                    label = path.relative_to(self.target).as_posix()
                    try:
                        lines = path.read_text(encoding="utf-8").splitlines()
                    except (OSError, UnicodeDecodeError) as exc:
                        self.error(
                            f"cannot inspect runtime-facing Markdown {label}: {exc}"
                        )
                        continue
                    self.validate_runtime_markdown_lines(lines, label)
            managed = self.managed_root_guidance_lines(runtime)
            if managed is not None:
                lines, label = managed
                self.validate_runtime_markdown_lines(
                    lines, label, managed_block=True
                )

    def validate_memory_index(self, path: Path) -> None:
        text = path.read_text(encoding="utf-8")
        self.validate_memory_document_budget(path, "memory index", text)
        header_sets = (
            ENGLISH_MEMORY_HEADERS,
            LEGACY_KOREAN_MEMORY_HEADERS,
        )
        header_columns = next(
            (
                tuple(
                    column.strip()
                    for column in line.strip().strip("|").split("|")
                )
                for line in text.splitlines()
                if line.lstrip().startswith("|")
                and tuple(
                    column.strip()
                    for column in line.strip().strip("|").split("|")
                )
                in header_sets
            ),
            None,
        )
        if header_columns is None:
            self.error("memory index is missing required table headers")
            return
        if (
            self.requires_english_artifacts()
            and header_columns != ENGLISH_MEMORY_HEADERS
        ):
            self.error(
                "memory index must use English table headers when "
                "communication.artifact_language is 'en'"
            )

        seen_ids: set[str] = set()
        seen_paths: set[str] = set()
        for line in text.splitlines():
            if not line.lstrip().startswith("|"):
                continue
            columns = [column.strip() for column in line.strip().strip("|").split("|")]
            if len(columns) != 7:
                self.error(f"memory index has a malformed table row: {line!r}")
                continue
            if tuple(columns) in header_sets or columns[0] == "---":
                continue
            if all(set(column) <= {"-", ":"} for column in columns):
                continue
            entry_id, relative, summary, read_when, _source, _verified, status = columns
            if self.requires_english_artifacts():
                for field, value in (
                    ("Summary", summary),
                    ("Read when", read_when),
                ):
                    letters = non_english_letters(value)
                    if letters:
                        self.error(
                            f"memory index {field} must use English prose: "
                            f"{entry_id!r} contains {letters!r}"
                        )
            if status not in MEMORY_STATUSES:
                self.error(f"memory index has unsupported status {status!r}: {entry_id!r}")
                continue
            if status == "empty":
                if entry_id != "-" or relative != "-":
                    self.error("memory index empty row must use '-' for ID and path")
                continue
            if not self.identifier(entry_id, "memory index ID"):
                continue
            if (
                self.memory_max_summary_chars > 0
                and len(summary) > self.memory_max_summary_chars
            ):
                self.error(
                    f"memory index summary exceeds memory.max_summary_chars "
                    f"({len(summary)} > {self.memory_max_summary_chars}): {entry_id!r}"
                )
            if entry_id in seen_ids:
                self.error(f"memory index contains duplicate ID: {entry_id!r}")
            seen_ids.add(entry_id)
            normalized = relative.replace("\\", "/")
            if normalized in seen_paths:
                self.error(f"memory index contains duplicate path: {relative!r}")
            seen_paths.add(normalized)
            if normalized in {"harness/state/state.json", "harness/ledger/journal.jsonl"}:
                self.error(f"memory index must not duplicate state or event history: {relative!r}")
            if status == "active" and self.safe_relative(relative, "memory index path"):
                candidate = (self.target / relative).resolve()
                if not candidate.is_relative_to(self.target) or not candidate.is_file():
                    self.error(f"memory index active path does not exist: {relative!r}")
                elif candidate.suffix.lower() == ".md":
                    try:
                        candidate_text = candidate.read_text(encoding="utf-8")
                    except UnicodeDecodeError:
                        self.error(f"memory index active Markdown is not UTF-8: {relative!r}")
                    else:
                        self.validate_memory_document_budget(
                            candidate, f"memory document {relative!r}", candidate_text
                        )

    def validate_memory_document_budget(
        self, path: Path, label: str, text: str
    ) -> None:
        if self.memory_max_document_lines < 1:
            return
        line_count = len(text.splitlines())
        if (
            line_count > self.memory_max_document_lines
            and "<!-- reading-budget exception:" not in text
            and "<!-- 문서규율 예외:" not in text
        ):
            self.error(
                f"{label} exceeds memory.max_document_lines "
                f"({line_count} > {self.memory_max_document_lines}): "
                f"{path.relative_to(self.target)!s}"
            )

    def validate_targeted_suite(self) -> None:

        path = self.harness_root / "evaluation" / "suites" / "targeted.json"
        if not path.is_file():
            return
        try:
            suite = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            self.error(f"evaluation/suites/targeted.json is invalid: {exc}")
            return
        suite = self.object(suite, "evaluation/suites/targeted.json")
        self.check_keys(
            suite,
            "evaluation/suites/targeted.json",
            {"schema_version", "checks"},
            {"schema_version", "checks"},
        )
        if suite.get("schema_version") != "1.0":
            self.error("targeted suite schema_version must be '1.0'")
        checks = self.object(suite.get("checks"), "targeted suite checks")
        self.check_keys(checks, "targeted suite checks", TARGETED_REASONS, TARGETED_REASONS)
        for reason in TARGETED_REASONS:
            check = self.object(checks.get(reason), f"targeted suite checks.{reason}")
            self.check_keys(
                check,
                f"targeted suite checks.{reason}",
                {"metrics"},
                {"metrics"},
            )
            metrics = self.string_list(
                check.get("metrics"), f"targeted suite checks.{reason}.metrics"
            )
            if not metrics:
                self.error(f"targeted suite checks.{reason}.metrics must not be empty")
            if len(metrics) != len(set(metrics)):
                self.error(f"targeted suite checks.{reason}.metrics contains duplicates")
            if any(not metric.strip() for metric in metrics):
                self.error(
                    f"targeted suite checks.{reason}.metrics must contain non-empty strings"
                )

    def validate_self_evaluation_state(self) -> None:
        path = self.harness_root / "state" / "self-evaluation.json"
        if not path.is_file():
            return
        try:
            state = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            self.error(f"state/self-evaluation.json is invalid: {exc}")
            return
        state = self.object(state, "state/self-evaluation.json")
        state_keys = {
            "schema_version",
            "current_unit",
            "units_since_full",
            "last_full_at",
            "cooldown_remaining_units",
            "pending_events",
            "baseline",
            "recent",
            "rolling",
            "hashes",
            "acknowledged",
            "last_decision",
        }
        self.check_keys(
            state,
            "state/self-evaluation.json",
            state_keys,
            state_keys,
        )
        if state.get("schema_version") != "1.0":
            self.error("self-evaluation state schema_version must be '1.0'")
        current_unit = state.get("current_unit")
        if current_unit is not None and not isinstance(current_unit, str):
            self.error("self-evaluation state current_unit must be null or a string")
        last_full_at = state.get("last_full_at")
        if last_full_at is not None and not isinstance(last_full_at, str):
            self.error("self-evaluation state last_full_at must be null or a string")
        for field in ("units_since_full", "cooldown_remaining_units"):
            self.number_in_range(
                state.get(field),
                f"self-evaluation state {field}",
                minimum=0,
                integer=True,
            )
        pending_events = self.string_list(
            state.get("pending_events"), "self-evaluation state pending_events"
        )
        if len(pending_events) != len(set(pending_events)):
            self.error("self-evaluation state pending_events contains duplicates")
        for event in pending_events:
            self.identifier(event, "self-evaluation state pending_events")

        baseline = self.object(state.get("baseline"), "self-evaluation state baseline")
        self.check_keys(
            baseline,
            "self-evaluation state baseline",
            {"success_rate", "cost_per_unit"},
            {"success_rate", "cost_per_unit"},
        )
        self.optional_number_in_range(
            baseline.get("success_rate"),
            "self-evaluation state baseline.success_rate",
            minimum=0,
            maximum=1,
        )
        self.optional_number_in_range(
            baseline.get("cost_per_unit"),
            "self-evaluation state baseline.cost_per_unit",
            minimum=0,
        )

        recent = self.object(state.get("recent"), "self-evaluation state recent")
        self.check_keys(
            recent,
            "self-evaluation state recent",
            {"samples", "success_rate", "cost_per_unit"},
            {"samples", "success_rate", "cost_per_unit"},
        )
        self.number_in_range(
            recent.get("samples"),
            "self-evaluation state recent.samples",
            minimum=0,
            integer=True,
        )
        self.optional_number_in_range(
            recent.get("success_rate"),
            "self-evaluation state recent.success_rate",
            minimum=0,
            maximum=1,
        )
        self.optional_number_in_range(
            recent.get("cost_per_unit"),
            "self-evaluation state recent.cost_per_unit",
            minimum=0,
        )

        rolling = self.object(state.get("rolling"), "self-evaluation state rolling")
        rolling_keys = {
            "window_units",
            "passed",
            "failed",
            "retries",
            "operation_cost",
            "evaluation_cost",
        }
        self.check_keys(
            rolling,
            "self-evaluation state rolling",
            rolling_keys,
            rolling_keys,
        )
        self.number_in_range(
            rolling.get("window_units"),
            "self-evaluation state rolling.window_units",
            minimum=1,
            integer=True,
        )
        for field in ("passed", "failed", "retries"):
            self.number_in_range(
                rolling.get(field),
                f"self-evaluation state rolling.{field}",
                minimum=0,
                integer=True,
            )
        for field in ("operation_cost", "evaluation_cost"):
            self.number_in_range(
                rolling.get(field),
                f"self-evaluation state rolling.{field}",
                minimum=0,
            )

        hashes = self.object(state.get("hashes"), "self-evaluation state hashes")
        self.check_keys(
            hashes,
            "self-evaluation state hashes",
            {"canonical", "adapters"},
            {"canonical", "adapters"},
        )
        for field in ("canonical", "adapters"):
            if not isinstance(hashes.get(field), str):
                self.error(f"self-evaluation state hashes.{field} must be a string")

        acknowledged = self.object(
            state.get("acknowledged"), "self-evaluation state acknowledged"
        )
        self.check_keys(
            acknowledged,
            "self-evaluation state acknowledged",
            {"coldstart_fail", "fail_counts"},
            {"coldstart_fail", "fail_counts"},
        )
        if not isinstance(acknowledged.get("coldstart_fail"), bool):
            self.error(
                "self-evaluation state acknowledged.coldstart_fail must be a boolean"
            )
        fail_counts = self.object(
            acknowledged.get("fail_counts"),
            "self-evaluation state acknowledged.fail_counts",
        )
        for failure_key, count in fail_counts.items():
            if not isinstance(failure_key, str) or not failure_key:
                self.error(
                    "self-evaluation state acknowledged.fail_counts keys must be "
                    "non-empty strings"
                )
            if not isinstance(count, int) or isinstance(count, bool) or count < 0:
                self.error(
                    "self-evaluation state acknowledged.fail_counts values must be "
                    "nonnegative integers"
                )

        last_decision = self.object(
            state.get("last_decision"), "self-evaluation state last_decision"
        )
        self.check_keys(
            last_decision,
            "self-evaluation state last_decision",
            {"decision", "reasons", "verdict"},
            {"decision", "reasons", "verdict"},
        )
        decision = last_decision.get("decision")
        if not isinstance(decision, str) or decision not in {
            "none",
            "targeted",
            "full",
        }:
            self.error("self-evaluation state last_decision.decision is invalid")
        reasons = self.string_list(
            last_decision.get("reasons"), "self-evaluation state last_decision.reasons"
        )
        if len(reasons) != len(set(reasons)):
            self.error("self-evaluation state last_decision.reasons contains duplicates")
        verdict = last_decision.get("verdict")
        if verdict is not None and verdict not in HARNESS_EFFECT_VERDICTS:
            self.error("self-evaluation state last_decision.verdict is invalid")

    def validate_adapters(self) -> None:
        for runtime in sorted(self.runtime_targets):
            if runtime not in self.provider_contracts:
                continue
            validator = getattr(self, f"validate_{runtime}", None)
            if validator is None:
                self.error(f"provider has no adapter validator: {runtime}")
                continue
            validator()

    def validate_managed_skills(self, runtime: str, label: str) -> None:
        skill_root = self.provider_path(runtime, "skill_root")
        for skill_id in self.skill_ids:
            path = skill_root / skill_id / "SKILL.md"
            if not path.is_file():
                self.error(f"missing {label} skill: {skill_id}")
            elif self.frontmatter_name(path) != skill_id:
                self.error(f"{label} skill frontmatter name mismatch: {skill_id}")
            else:
                self.require_json_frontmatter_string(
                    path, "description", f"{label} skill {skill_id}"
                )
                canonical = self.canonical_skill_path_for_id(skill_id)
                if canonical and canonical.is_file() and path.read_bytes() != canonical.read_bytes():
                    self.error(
                        f"{label} skill differs from canonical instructions: {skill_id}"
                    )
        actual_skills = (
            {
                path.name
                for path in skill_root.glob(f"{self.namespace}*")
                if path.is_dir() and (path / "SKILL.md").is_file()
            }
            if skill_root.is_dir()
            else set()
        )
        if actual_skills != self.skill_ids:
            self.error(
                f"{label} managed-skill parity mismatch: "
                f"expected {sorted(self.skill_ids)}, got {sorted(actual_skills)}"
            )

    def validate_claude(self) -> None:
        marker = f"<!-- harness-factory:start {self.namespace} -->"
        self.require_single_marker(self.provider_path("claude", "root_guidance"), marker)
        expected_agents = {f"{self.namespace}-{role}" for role in self.agent_ids}
        agent_root = self.provider_path("claude", "agent_root")
        actual_agents = (
            {path.stem for path in agent_root.glob(f"{self.namespace}-*.md")}
            if agent_root.is_dir()
            else set()
        )
        if actual_agents != expected_agents:
            self.error(
                "Claude managed-agent parity mismatch: "
                f"expected {sorted(expected_agents)}, got {sorted(actual_agents)}"
            )
        agents_by_id = {agent["id"]: agent for agent in self.spec.get("agents", [])}
        harness_root = self.harness_root_text()
        for role in self.agent_ids:
            agent_name = f"{self.namespace}-{role}"
            path = agent_root / f"{agent_name}.md"
            if not path.is_file():
                self.error(f"missing Claude agent: {path.relative_to(self.target)}")
                continue
            fields = self.frontmatter_fields(path)
            claude_frontmatter_keys = {
                "name",
                "description",
                "model",
                "tools",
                "disallowedTools",
                "permissionMode",
            }
            self.check_keys(
                fields,
                f"Claude agent {path.name} frontmatter",
                claude_frontmatter_keys,
                claude_frontmatter_keys,
            )
            if fields.get("name") != agent_name:
                self.error(f"Claude agent frontmatter does not match filename: {path.name}")
            if fields.get("description") != agents_by_id[role].get("description"):
                self.error(f"Claude agent description parity mismatch: {path.name}")
            self.require_json_frontmatter_string(
                path, "description", f"Claude agent {path.name}"
            )
            agent_text = path.read_text(encoding="utf-8")
            for common_reference in (
                f"{harness_root}/harness-spec.json",
                f"{harness_root}/team/agents/{role}.md",
            ):
                if common_reference not in agent_text:
                    self.error(
                        f"Claude agent does not reference common contract "
                        f"{common_reference!r}: {path.name}"
                    )
            if self.markdown_body(path) != self.expected_claude_agent_body(role):
                self.error(f"Claude agent thin-wrapper parity mismatch: {path.name}")
            if fields.get("permissionMode") == "bypassPermissions":
                self.error(f"Claude agent must not bypass permissions: {path.name}")
            if agents_by_id[role].get("access") == "read-only":
                tools = self.comma_values(fields.get("tools", ""))
                disallowed = self.comma_values(fields.get("disallowedTools", ""))
                if tools != CLAUDE_READ_ONLY_TOOLS:
                    self.error(f"Claude read-only tools mapping mismatch: {path.name}")
                if not CLAUDE_READ_ONLY_DISALLOWED.issubset(disallowed):
                    self.error(
                        f"Claude read-only disallowedTools mapping mismatch: {path.name}"
                    )
                if fields.get("permissionMode") != "plan":
                    self.error(
                        f"Claude read-only permissionMode must be plan: {path.name}"
                    )
            else:
                tools = self.comma_values(fields.get("tools", ""))
                disallowed = self.comma_values(fields.get("disallowedTools", ""))
                if tools != CLAUDE_WORKSPACE_WRITE_TOOLS:
                    self.error(
                        f"Claude workspace-write tools mapping mismatch: {path.name}"
                    )
                if "NotebookEdit" not in disallowed:
                    self.error(
                        f"Claude workspace-write disallowedTools mapping mismatch: {path.name}"
                    )
                if fields.get("permissionMode") != "default":
                    self.error(
                        f"Claude workspace-write permissionMode must be default: {path.name}"
                    )
        self.validate_managed_skills("claude", "Claude")

    def validate_codex(self) -> None:
        marker = f"<!-- harness-factory:start {self.namespace} -->"
        self.require_single_marker(self.provider_path("codex", "root_guidance"), marker)
        self.validate_managed_skills("codex", "Codex")

        codex_agent_root = self.provider_path("codex", "agent_root")
        expected_agent_names = {f"{self.namespace}-{role}" for role in self.agent_ids}
        actual_agent_names = (
            {path.stem for path in codex_agent_root.glob(f"{self.namespace}-*.toml")}
            if codex_agent_root.is_dir()
            else set()
        )
        if actual_agent_names != expected_agent_names:
            self.error(
                "Codex managed-agent parity mismatch: "
                f"expected {sorted(expected_agent_names)}, got {sorted(actual_agent_names)}"
            )

        config_path = self.provider_path("codex", "config")
        try:
            config = tomllib.loads(config_path.read_text(encoding="utf-8"))
        except OSError as exc:
            self.error(f"cannot read .codex/config.toml: {exc}")
            return
        except tomllib.TOMLDecodeError as exc:
            self.error(f".codex/config.toml is invalid TOML: {exc}")
            return
        agent_settings = config.get("agents")
        if not isinstance(agent_settings, dict):
            self.error(".codex/config.toml is missing [agents]")
            return
        limits = self.spec.get("limits", {})
        max_threads = agent_settings.get("max_threads")
        max_depth = agent_settings.get("max_depth")
        if not isinstance(max_threads, int) or isinstance(max_threads, bool):
            self.error("Codex max_threads must be an integer")
        elif max_threads < limits.get("max_parallelism", 1):
            self.error("Codex max_threads is lower than spec max_parallelism")
        if not isinstance(max_depth, int) or isinstance(max_depth, bool):
            self.error("Codex max_depth must be an integer")
        elif max_depth < limits.get("max_delegation_depth", 1):
            self.error("Codex max_depth is lower than spec max_delegation_depth")
        agents_by_id = {agent["id"]: agent for agent in self.spec.get("agents", [])}
        for role in self.agent_ids:
            agent_name = f"{self.namespace}-{role}"
            agent_path = codex_agent_root / f"{agent_name}.toml"
            try:
                agent_config = tomllib.loads(agent_path.read_text(encoding="utf-8"))
            except OSError as exc:
                self.error(f"missing Codex agent file {agent_path.name}: {exc}")
                continue
            except tomllib.TOMLDecodeError as exc:
                self.error(f"invalid Codex agent TOML {agent_path.name}: {exc}")
                continue
            if agent_config.get("name") != agent_name:
                self.error(f"Codex agent name mismatch: {agent_path.name}")
            codex_agent_keys = {
                "name",
                "description",
                "model",
                "model_reasoning_effort",
                "sandbox_mode",
                "developer_instructions",
            }
            self.check_keys(
                agent_config,
                f"Codex agent {agent_path.name}",
                codex_agent_keys,
                codex_agent_keys,
            )
            expected_description = agents_by_id[role].get("description")
            if agent_config.get("description") != expected_description:
                self.error(f"Codex agent description parity mismatch: {agent_path.name}")
            instructions = agent_config.get("developer_instructions")
            if not isinstance(instructions, str) or not instructions.strip():
                self.error(f"Codex agent lacks developer_instructions: {agent_path.name}")
            else:
                harness_root = self.harness_root_text()
                for common_reference in (
                    f"{harness_root}/harness-spec.json",
                    f"{harness_root}/team/agents/{role}.md",
                ):
                    if common_reference not in instructions:
                        self.error(
                            f"Codex agent does not reference common contract "
                            f"{common_reference!r}: {agent_path.name}"
                        )
                if instructions != self.expected_codex_agent_instructions(role):
                    self.error(
                        f"Codex agent thin-wrapper parity mismatch: {agent_path.name}"
                    )
            if agent_config.get("sandbox_mode") != agents_by_id[role].get("access"):
                self.error(f"Codex agent access parity mismatch: {agent_path.name}")

    def validate_gemini(self) -> None:
        marker = f"<!-- harness-factory:start {self.namespace} -->"
        self.require_single_marker(
            self.provider_path("gemini", "root_guidance"), marker
        )
        self.validate_managed_skills("gemini", "Gemini")

        agent_root = self.provider_path("gemini", "agent_root")
        expected_agents = {f"{self.namespace}-{role}" for role in self.agent_ids}
        actual_agents = (
            {path.stem for path in agent_root.glob(f"{self.namespace}-*.md")}
            if agent_root.is_dir()
            else set()
        )
        if actual_agents != expected_agents:
            self.error(
                "Gemini managed-agent parity mismatch: "
                f"expected {sorted(expected_agents)}, got {sorted(actual_agents)}"
            )

        agents_by_id = {agent["id"]: agent for agent in self.spec.get("agents", [])}
        harness_root = self.harness_root_text()
        gemini_keys = {
            "name",
            "description",
            "kind",
            "tools",
            "model",
            "temperature",
            "max_turns",
        }
        for role in self.agent_ids:
            agent_name = f"{self.namespace}-{role}"
            path = agent_root / f"{agent_name}.md"
            if not path.is_file():
                self.error(f"missing Gemini agent: {path.relative_to(self.target)}")
                continue
            fields = self.frontmatter_fields(path)
            self.check_keys(
                fields,
                f"Gemini agent {path.name} frontmatter",
                gemini_keys,
                gemini_keys,
            )
            if fields.get("name") != agent_name:
                self.error(
                    f"Gemini agent frontmatter does not match filename: {path.name}"
                )
            if fields.get("description") != agents_by_id[role].get("description"):
                self.error(f"Gemini agent description parity mismatch: {path.name}")
            self.require_json_frontmatter_string(
                path, "description", f"Gemini agent {path.name}"
            )
            if fields.get("kind") != "local":
                self.error(f"Gemini agent kind must be local: {path.name}")
            tools = fields.get("tools")
            if (
                not isinstance(tools, list)
                or not tools
                or not all(isinstance(tool, str) and tool for tool in tools)
            ):
                self.error(
                    f"Gemini agent tools must be a non-empty string list: {path.name}"
                )
            elif len(tools) != len(set(tools)):
                self.error(f"Gemini agent tools contains duplicates: {path.name}")
            else:
                access = agents_by_id[role].get("access")
                expected_tools = (
                    GEMINI_READ_ONLY_TOOLS
                    if access == "read-only"
                    else GEMINI_WORKSPACE_WRITE_TOOLS
                )
                if set(tools) != expected_tools:
                    self.error(
                        f"Gemini agent tool access parity mismatch: {path.name}; "
                        f"expected {sorted(expected_tools)}, got {sorted(set(tools))}"
                    )
            model = fields.get("model")
            if not isinstance(model, str) or not model.strip():
                self.error(f"Gemini agent model must be a non-empty string: {path.name}")
            temperature = fields.get("temperature")
            if (
                isinstance(temperature, bool)
                or not isinstance(temperature, (int, float))
                or temperature != 0
            ):
                self.error(f"Gemini agent temperature must be 0: {path.name}")
            max_turns = fields.get("max_turns")
            if (
                not isinstance(max_turns, int)
                or isinstance(max_turns, bool)
                or max_turns < 1
            ):
                self.error(
                    f"Gemini agent max_turns must be a positive integer: {path.name}"
                )
            agent_text = path.read_text(encoding="utf-8")
            for common_reference in (
                f"{harness_root}/harness-spec.json",
                f"{harness_root}/team/agents/{role}.md",
            ):
                if common_reference not in agent_text:
                    self.error(
                        f"Gemini agent does not reference common contract "
                        f"{common_reference!r}: {path.name}"
                    )
            if self.markdown_body(path) != self.expected_gemini_agent_body(role):
                self.error(f"Gemini agent thin-wrapper parity mismatch: {path.name}")

    def validate_placeholders(self) -> None:
        roots = [self.harness_root]
        for runtime in sorted(self.runtime_targets):
            contract = self.provider_contracts.get(runtime, {})
            for key in ("root_guidance", "skill_root", "agent_root", "config"):
                if key in contract:
                    roots.append(self.provider_path(runtime, key))
        seen: set[Path] = set()
        for root in roots:
            root = root.resolve()
            if root in seen:
                continue
            seen.add(root)
            paths = [root] if root.is_file() else list(root.rglob("*")) if root.exists() else []
            for path in paths:
                if not path.is_file():
                    continue
                try:
                    text = path.read_text(encoding="utf-8")
                except UnicodeDecodeError:
                    continue
                if re.search(r"\{\{[^{}]+\}\}", text):
                    self.error(f"unrendered placeholder: {path.relative_to(self.target)}")

    def require_single_marker(self, path: Path, marker: str) -> None:
        try:
            count = path.read_text(encoding="utf-8").count(marker)
        except OSError:
            self.error(f"missing root guidance file: {path.name}")
            return
        if count != 1:
            self.error(f"{path.name} must contain exactly one {marker!r} marker, got {count}")

    def markdown_body(self, path: Path) -> str:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            return ""
        if not text.startswith("---\n"):
            return ""
        end = text.find("\n---", 4)
        if end < 0:
            return ""
        return text[end + len("\n---") :].strip()

    def expected_claude_agent_body(self, role: str) -> str:
        harness_root = self.harness_root_text()
        return (
            f"# {role}\n\n"
            f"Read `{harness_root}/harness-spec.json` and "
            f"`{harness_root}/team/agents/{role}.md` first.\n\n"
            "Treat the common role file as canonical. Return evidence and any "
            "verification not run. Name the next role "
            f"`{self.namespace}-<role-id>`."
        )

    def expected_codex_agent_instructions(self, role: str) -> str:
        harness_root = self.harness_root_text()
        return (
            f"Read {harness_root}/harness-spec.json and "
            f"{harness_root}/team/agents/{role}.md first.\n\n"
            "Treat the common role file as the canonical instructions. Follow its "
            "input, output, access, approval-gate, and handoff contract. Return "
            "evidence and any verification not run."
        )

    def expected_gemini_agent_body(self, role: str) -> str:
        harness_root = self.harness_root_text()
        return (
            f"# {role}\n\n"
            f"Read `{harness_root}/harness-spec.json` and "
            f"`{harness_root}/team/agents/{role}.md` first.\n\n"
            "Treat the common role file as canonical. Return evidence and any "
            "verification not run. Do not call another Gemini subagent; return the "
            "next handoff to the main orchestrator."
        )

    def frontmatter_raw_fields(self, path: Path) -> dict[str, str]:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            return {}
        if not text.startswith("---\n"):
            return {}
        end = text.find("\n---", 4)
        if end < 0:
            return {}
        result: dict[str, str] = {}
        for line in text[4:end].splitlines():
            key, separator, value = line.partition(":")
            if separator:
                result[key.strip()] = value.strip()
        return result

    def frontmatter_fields(self, path: Path) -> dict[str, object]:
        result: dict[str, object] = {}
        for key, raw in self.frontmatter_raw_fields(path).items():
            try:
                result[key] = json.loads(raw)
            except json.JSONDecodeError:
                result[key] = raw.strip("'\"")
        return result

    def frontmatter_name(self, path: Path) -> str:
        value = self.frontmatter_fields(path).get("name", "")
        return value if isinstance(value, str) else ""

    def require_json_frontmatter_string(
        self, path: Path, key: str, label: str
    ) -> None:
        raw = self.frontmatter_raw_fields(path).get(key)
        if raw is None:
            self.error(f"{label} is missing frontmatter {key}")
            return
        try:
            value = json.loads(raw)
        except json.JSONDecodeError:
            self.error(f"{label} frontmatter {key} must be a JSON-quoted string")
            return
        if not isinstance(value, str):
            self.error(f"{label} frontmatter {key} must be a JSON string")

    def canonical_skill_path(self, skill: dict) -> Path | None:
        instructions = skill.get("instructions")
        if not isinstance(instructions, str) or not instructions:
            return None
        path = (self.target / instructions).resolve()
        if not path.is_relative_to(self.harness_root):
            self.error(
                f"skill instructions escape common harness root: {skill.get('id')!r}"
            )
            return None
        return path

    def canonical_skill_path_for_id(self, skill_id: str) -> Path | None:
        for skill in self.spec.get("skills", []):
            if skill.get("id") == skill_id:
                return self.canonical_skill_path(skill)
        return None

    def comma_values(self, value: object) -> set[str]:
        if isinstance(value, list) and all(isinstance(item, str) for item in value):
            return set(value)
        if isinstance(value, str):
            return {item.strip() for item in value.split(",") if item.strip()}
        return set()

    def harness_root_text(self) -> str:
        value = self.spec.get("harness", {}).get("root")
        return value.replace("\\", "/").rstrip("/") if isinstance(value, str) else ""

    def provider_path(self, runtime: str, key: str) -> Path:
        contract = self.provider_contracts.get(runtime, {})
        value = contract.get(key)
        if not isinstance(value, str) or not value:
            self.error(f"provider {runtime!r} is missing path field {key!r}")
            self.provider_path_preflight_failed = True
            return self.target
        try:
            resolved = (self.target / value).resolve()
        except (OSError, RuntimeError) as exc:
            self.error(
                f"provider {runtime!r} path field {key!r} cannot be resolved: {exc}"
            )
            self.provider_path_preflight_failed = True
            return self.target
        if not resolved.is_relative_to(self.target):
            self.error(
                f"provider {runtime!r} path field {key!r} resolves outside target: "
                f"{value!r}"
            )
            self.provider_path_preflight_failed = True
            return self.target
        return resolved

    def number_in_range(
        self,
        value: object,
        label: str,
        *,
        minimum: float | None = None,
        maximum: float | None = None,
        exclusive_minimum: bool = False,
        integer: bool = False,
    ) -> bool:
        if integer:
            valid_type = isinstance(value, int) and not isinstance(value, bool)
        else:
            valid_type = isinstance(value, (int, float)) and not isinstance(value, bool)
        if not valid_type:
            kind = "integer" if integer else "number"
            self.error(f"{label} must be a {kind}")
            return False
        number = float(value)
        if not math.isfinite(number):
            self.error(f"{label} must be finite")
            return False
        if minimum is not None:
            if exclusive_minimum and number <= minimum:
                self.error(f"{label} must be greater than {minimum}")
                return False
            if not exclusive_minimum and number < minimum:
                self.error(f"{label} must be at least {minimum}")
                return False
        if maximum is not None and number > maximum:
            self.error(f"{label} must be at most {maximum}")
            return False
        return True

    def optional_number_in_range(
        self,
        value: object,
        label: str,
        *,
        minimum: float | None = None,
        maximum: float | None = None,
    ) -> bool:
        if value is None:
            return True
        return self.number_in_range(
            value,
            label,
            minimum=minimum,
            maximum=maximum,
        )

    def unique_ids(self, values: list[dict], label: str) -> set[str]:
        result: set[str] = set()
        for index, value in enumerate(values):
            item_id = self.identifier(value.get("id"), f"{label}[{index}].id")
            if item_id in result:
                self.error(f"duplicate {label} id: {item_id}")
            if item_id:
                result.add(item_id)
        return result

    def check_keys(
        self,
        value: dict,
        label: str,
        required: set[str],
        allowed: set[str],
    ) -> None:
        missing = required - set(value)
        unknown = set(value) - allowed
        if missing:
            self.error(f"{label} is missing keys: {sorted(missing)}")
        if unknown:
            self.error(f"{label} has unsupported keys: {sorted(unknown)}")

    def identifier(self, value: object, label: str) -> str:
        if not isinstance(value, str) or ID_RE.fullmatch(value) is None:
            self.error(f"{label} must be lower-kebab-case")
            return ""
        return value

    def safe_relative(self, value: object, label: str) -> bool:
        if not isinstance(value, str) or not value:
            self.error(f"{label} must be a non-empty relative path")
            return False
        normalized = value.replace("\\", "/")
        if normalized.startswith("/") or re.match(r"^[A-Za-z]:/", normalized):
            self.error(f"{label} must not be absolute: {value!r}")
            return False
        if ".." in normalized.split("/"):
            self.error(f"{label} must not traverse outside the target: {value!r}")
            return False
        return True

    def object(self, value: object, label: str) -> dict:
        if not isinstance(value, dict):
            self.error(f"{label} must be an object")
            return {}
        return value

    def list_of_objects(self, value: object, label: str) -> list[dict]:
        if not isinstance(value, list):
            self.error(f"{label} must be an array")
            return []
        if not all(isinstance(item, dict) for item in value):
            self.error(f"{label} must contain only objects")
            return []
        return value

    def string_list(self, value: object, label: str) -> list[str]:
        if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
            self.error(f"{label} must be an array of strings")
            return []
        return value


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("target", type=Path, help="target project containing harness/")
    parser.add_argument(
        "--spec",
        type=Path,
        help="explicit spec path (default: <target>/harness/harness-spec.json)",
    )
    parser.add_argument(
        "--provider-path-preflight",
        action="store_true",
        help="validate spec and resolved provider paths without reading adapters",
    )
    parser.add_argument(
        "--construction-mode",
        choices=sorted(CONSTRUCTION_MODES),
        help="construction mode; must be paired with --delta-plan",
    )
    parser.add_argument(
        "--delta-plan",
        type=Path,
        help=(
            "construction receipt path relative to target or absolute; must be paired "
            "with --construction-mode"
        ),
    )
    args = parser.parse_args()
    target = args.target.expanduser().resolve()
    spec_path = (
        args.spec.expanduser().resolve()
        if args.spec
        else target / "harness" / "harness-spec.json"
    )
    validator = Validator(
        target,
        spec_path,
        construction_mode=args.construction_mode,
        delta_plan_path=args.delta_plan,
    )
    errors = (
        validator.validate_provider_path_preflight()
        if args.provider_path_preflight
        else validator.validate()
    )
    if errors:
        print("runtime-neutral harness validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    action = (
        "provider path preflight"
        if args.provider_path_preflight
        else "runtime-neutral harness validation"
    )
    print(f"{action} passed: {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

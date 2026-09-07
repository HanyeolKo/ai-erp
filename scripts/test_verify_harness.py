"""Exercise the checks that protect the UI/UX routing and installed skills."""
import importlib.util
import json
from pathlib import Path
import shutil
import stat
import subprocess
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
module_spec = importlib.util.spec_from_file_location("verify_harness", ROOT / "scripts/verify-harness.py")
verifier = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(verifier)


class HarnessContractTests(unittest.TestCase):
    def setUp(self):
        self.parent = (ROOT / "tmp").resolve()
        self.parent.mkdir(exist_ok=True)
        self.root = self.parent / ("harness-test-" + uuid.uuid4().hex)
        self.root.mkdir()
        for directory in ("harness", "vendor", "scripts"):
            shutil.copytree(ROOT / directory, self.root / directory,
                            ignore=shutil.ignore_patterns("__pycache__"))
        (self.root / ".codex/agents").mkdir(parents=True)
        shutil.copyfile(ROOT / ".codex/config.toml", self.root / ".codex/config.toml")
        for path in (ROOT / ".codex/agents").glob("ai-erp-*.toml"):
            shutil.copyfile(path, self.root / ".codex/agents" / path.name)
        for path in (ROOT / ".agents/skills").glob("ai-erp*"):
            shutil.copytree(path, self.root / ".agents/skills" / path.name)
        shutil.copyfile(ROOT / "AGENTS.md", self.root / "AGENTS.md")

    def tearDown(self):
        if self.root.resolve().parent != self.parent:
            raise RuntimeError("Test cleanup escaped its workspace")
        def remove_readonly(function, path, exception):
            if not isinstance(exception, PermissionError) or not Path(path).resolve().is_relative_to(self.root.resolve()):
                raise exception
            Path(path).chmod(stat.S_IWRITE | stat.S_IREAD)
            function(path)
        shutil.rmtree(self.root, onexc=remove_readonly)

    def errors(self):
        return "\n".join(verifier.verify_project(self.root))

    def change_spec(self, change):
        path = self.root / "harness/harness-spec.json"
        spec = json.loads(path.read_text(encoding="utf-8"))
        change(spec)
        path.write_text(json.dumps(spec), encoding="utf-8")

    def test_installed_harness_with_inherited_model_passes(self):
        self.assertEqual("", self.errors())

    def test_skill_projection_drift_fails(self):
        path = self.root / ".agents/skills/ai-erp-ui-ux/SKILL.md"
        path.write_text(path.read_text(encoding="utf-8") + "\nDrift.\n", encoding="utf-8")
        self.assertIn("differs from canonical", self.errors())

    def test_missing_sandbox_is_not_hidden_by_model_compatibility(self):
        path = self.root / ".codex/agents/ai-erp-ui-ux-designer.toml"
        lines = path.read_text(encoding="utf-8").splitlines()
        path.write_text("\n".join(line for line in lines if not line.startswith("sandbox_mode")), encoding="utf-8")
        self.assertIn("sandbox_mode", self.errors())

    def test_bypassing_designer_fails(self):
        def bypass(spec):
            spec["orchestration"]["handoffs"] = [{
                "from": "router", "to": "reviewer", "when": "Any screen request",
                "artifacts": ["harness/templates/UI-REVIEW.md"],
            }]
        self.change_spec(bypass)
        self.assertIn("UI routing", self.errors())

    def test_domain_skill_cannot_route_to_general_agent(self):
        def change(spec):
            next(s for s in spec["skills"] if s["id"] == "ai-erp-ui-ux")["entry_agent"] = "router"
        self.change_spec(change)
        self.assertIn("UI skill", self.errors())

    def test_vendor_tampering_fails(self):
        path = self.root / "vendor/ux-skills/frontend-design/SKILL.md"
        path.write_bytes(path.read_bytes() + b"\nChanged upstream instructions.\n")
        self.assertIn("SHA-256", self.errors())

    def test_missing_vendor_source_fails(self):
        (self.root / "vendor/ux-skills/frontend-design/SKILL.md").unlink()
        self.assertIn("missing", self.errors())

    def test_unregistered_vendor_file_fails(self):
        (self.root / "vendor/ux-skills/frontend-design/injected.py").write_text("pass\n", encoding="utf-8")
        self.assertIn("unlocked vendor file", self.errors())

    def test_model_override_cannot_silently_break_inheritance(self):
        path = self.root / ".codex/agents/ai-erp-ui-ux-designer.toml"
        path.write_text('model = "runtime-selected"\n' + path.read_text(encoding="utf-8"), encoding="utf-8")
        self.assertIn("model inheritance", self.errors())

    def test_private_codex_files_are_not_required_in_git(self):
        (self.root / ".gitignore").write_text(".codex/auth.json\n.codex/config.local.toml\n", encoding="utf-8")
        (self.root / ".codex/auth.json").write_text('{"test": "not-a-credential"}', encoding="utf-8")
        (self.root / ".codex/config.local.toml").write_text("# test-only local configuration\n", encoding="utf-8")
        subprocess.run(["git", "init", "--quiet", str(self.root)], check=True, capture_output=True)
        subprocess.run(["git", "-c", "core.autocrlf=false", "add", "--all"], cwd=self.root, check=True, capture_output=True)
        self.assertEqual([], verifier.verify_project(self.root, require_tracked=True))


if __name__ == "__main__":
    unittest.main()

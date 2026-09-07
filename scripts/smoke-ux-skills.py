#!/usr/bin/env python3
"""Confirm the installed, offline UI/UX search runtime returns relevant guidance."""
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SEARCH = ROOT / "vendor/ux-skills/ui-ux-pro-max/scripts/search.py"


def search(query, domain):
    result = subprocess.run(
        [sys.executable, "-B", str(SEARCH), query, "--domain", domain, "--json"],
        cwd=ROOT, check=True, capture_output=True, text=True, encoding="utf-8", timeout=30,
    )
    data = json.loads(result.stdout)
    if data.get("domain") != domain or not data.get("results"):
        raise ValueError(f"No relevant {domain} result for {query!r}")
    return data["results"]


def main():
    product = search("B2B SaaS dashboard", "product")
    if product[0].get("Product Type") != "SaaS (General)":
        raise ValueError("Product search no longer resolves to the pinned SaaS result")
    forms = search("error summary validation", "ux")
    if forms[0].get("Issue") != "Focusable Error Summary":
        raise ValueError("Form accessibility search lost its expected semantic match")
    print("UI/UX skill smoke passed: relevant SaaS and accessible form guidance, offline.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

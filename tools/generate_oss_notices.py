#!/usr/bin/env python3
"""Generate the in-app open-source attribution notice (#377, depmgmt-1).

Steps of Babylon ships a proprietary AAB that bundles Apache-2.0 / permissive third-party libraries.
Apache-2.0 §4(d) attaches an attribution obligation to the redistributed BINARY, so the shipped app must
preserve the notices. This script emits `app/src/main/res/raw/oss_notices.txt`, which `HelpScreen` renders
read-only ("Open-source notices" section).

Design (per ADR-0041):
- The **coordinate list** (group:name + version) is derived from `gradle/libs.versions.toml` (the single
  source of dependency versions) so it can't silently drift on a version bump.
- The **license bodies** are the hand-curated `LICENSES` map below — the version catalog carries no SPDX id
  or license text, so regeneration catches ADDED/REMOVED deps, NOT license-text changes. Re-run this script
  and commit the output whenever the shipping-dependency set changes (a release-checklist item).
- Only DIRECT shipping (`implementation`) deps are listed; test/debug/androidTest deps never ship. The
  forced-transitive `guava` (a security constraint, not a direct dep) is listed because it ships in the AAB.

Run:  python3 tools/generate_oss_notices.py    (from the repo root)
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CATALOG = REPO_ROOT / "gradle" / "libs.versions.toml"
OUT = REPO_ROOT / "app" / "src" / "main" / "res" / "raw" / "oss_notices.txt"

# --- License bodies (hand-curated) --------------------------------------------------------------------
APACHE_2_0 = (
    "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use these files except\n"
    "in compliance with the License. You may obtain a copy of the License at\n"
    "\n"
    "    https://www.apache.org/licenses/LICENSE-2.0\n"
    "\n"
    "Unless required by applicable law or agreed to in writing, software distributed under the License is\n"
    "distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or\n"
    "implied. See the License for the specific language governing permissions and limitations under the\n"
    "License."
)

SQLCIPHER_BSD = (
    "Copyright (c) 2008-2024, Zetetic LLC. All rights reserved.\n"
    "\n"
    "Redistribution and use in source and binary forms, with or without modification, are permitted\n"
    "provided that the following conditions are met:\n"
    "  * Redistributions of source code must retain the above copyright notice, this list of conditions\n"
    "    and the following disclaimer.\n"
    "  * Redistributions in binary form must reproduce the above copyright notice, this list of conditions\n"
    "    and the following disclaimer in the documentation and/or other materials provided with the\n"
    "    distribution.\n"
    "  * Neither the name of the ZETETIC LLC nor the names of its contributors may be used to endorse or\n"
    "    promote products derived from this software without specific prior written permission.\n"
    "\n"
    "THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES ARE\n"
    "DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,\n"
    "EXEMPLARY, OR CONSEQUENTIAL DAMAGES ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE."
)

ANDROID_SDK_LICENSE = (
    "Distributed under the Android Software Development Kit License Agreement\n"
    "(https://developer.android.com/studio/terms) / the Google APIs Terms of Service. Copyright Google LLC."
)

# License labels → body. Order here also fixes the section order in the output.
LICENSE_TEXT = {
    "Apache-2.0": APACHE_2_0,
    "BSD-3-Clause (Zetetic)": SQLCIPHER_BSD,
    "Android SDK License (Google)": ANDROID_SDK_LICENSE,
}

# --- Direct SHIPPING dependencies (mirrors the `implementation(...)` set in app/build.gradle.kts) ------
# Each entry: (display name, catalog version key OR literal, license label, copyright holder).
# `bom:composeBom` means the artifact version is governed by the Compose BOM (no own version.ref).
DEPS = [
    ("Jetpack Compose (UI, Graphics, Material 3, Material Icons)", "bom:composeBom", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX Core KTX", "coreKtx", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX Activity Compose", "activityCompose", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX Lifecycle (ViewModel/Runtime Compose)", "lifecycle", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX Navigation Compose", "navigationCompose", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX Room (Runtime, KTX)", "room", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX WorkManager", "workmanager", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX Hilt (Work, Navigation Compose)", "hiltWork", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX Profile Installer", "profileinstaller", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX Health Connect Client", "healthConnect", "Apache-2.0", "The Android Open Source Project"),
    ("AndroidX SQLite KTX", "sqliteKtx", "Apache-2.0", "The Android Open Source Project"),
    ("Dagger Hilt", "hilt", "Apache-2.0", "Google LLC"),
    ("Kotlin Coroutines (Android)", "coroutines", "Apache-2.0", "JetBrains s.r.o. and contributors"),
    ("Guava", "guava", "Apache-2.0", "Google LLC"),
    ("SQLCipher for Android", "sqlcipher", "BSD-3-Clause (Zetetic)", "Zetetic LLC"),
    ("Google Play Billing Library", "billingPlay", "Android SDK License (Google)", "Google LLC"),
    ("Google Mobile Ads SDK (Play Services Ads)", "playServicesAds", "Android SDK License (Google)", "Google LLC"),
    ("Google User Messaging Platform (UMP)", "userMessagingPlatform", "Android SDK License (Google)", "Google LLC"),
]


def parse_versions(catalog_text: str) -> dict[str, str]:
    """Parse the `[versions]` table of libs.versions.toml into {key: version}."""
    versions: dict[str, str] = {}
    in_versions = False
    for line in catalog_text.splitlines():
        stripped = line.strip()
        if stripped.startswith("["):
            in_versions = stripped == "[versions]"
            continue
        if not in_versions or not stripped or stripped.startswith("#"):
            continue
        m = re.match(r'([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"', stripped)
        if m:
            versions[m.group(1)] = m.group(2)
    return versions


def version_for(key: str, versions: dict[str, str]) -> str:
    if key.startswith("bom:"):
        bom_key = key.split(":", 1)[1]
        return f"managed by Compose BOM {versions.get(bom_key, '?')}"
    return versions.get(key, "?")


def build_notice(versions: dict[str, str]) -> str:
    lines: list[str] = []
    lines.append("OPEN-SOURCE NOTICES — Steps of Babylon")
    lines.append("")
    lines.append(
        "Steps of Babylon bundles the third-party open-source software listed below. This notice is provided\n"
        "to satisfy the attribution requirements of those licenses (including Apache License 2.0 section 4d).\n"
        "Generated from gradle/libs.versions.toml by tools/generate_oss_notices.py — re-run it if the\n"
        "shipping-dependency set changes."
    )
    lines.append("")
    lines.append("=" * 90)
    lines.append("BUNDLED LIBRARIES")
    lines.append("=" * 90)
    lines.append("")
    for name, ver_key, lic, holder in DEPS:
        lines.append(f"* {name}")
        lines.append(f"    Version:   {version_for(ver_key, versions)}")
        lines.append(f"    Copyright: {holder}")
        lines.append(f"    License:   {lic}")
        lines.append("")
    lines.append("=" * 90)
    lines.append("LICENSE TEXTS")
    lines.append("=" * 90)
    for label, body in LICENSE_TEXT.items():
        lines.append("")
        lines.append("-" * 90)
        lines.append(label)
        lines.append("-" * 90)
        lines.append(body)
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    if not CATALOG.exists():
        print(f"error: {CATALOG} not found (run from the repo root)", file=sys.stderr)
        return 1
    versions = parse_versions(CATALOG.read_text())
    notice = build_notice(versions)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(notice)
    print(f"wrote {OUT.relative_to(REPO_ROOT)} ({len(notice)} bytes, {len(DEPS)} libraries)")
    # Flag any dependency whose version key didn't resolve (a rename in the catalog).
    missing = [k for _, k, _, _ in DEPS if not k.startswith("bom:") and k not in versions]
    if missing:
        print(f"warning: unresolved version keys (catalog rename?): {missing}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

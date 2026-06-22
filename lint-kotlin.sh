#!/usr/bin/env bash
# Kotlin lint check (ktlint 1.8.0, pinned + SHA-256-verified).
# Usage:
#   ./lint-kotlin.sh           # check-only (fails on NEW violations; baseline suppresses existing)
#   ./lint-kotlin.sh --format  # auto-format (runs -F WITHOUT baseline so existing violations get fixed)
#
# The single source of truth for the ktlint version + hash is HERE — CI sources this script.
# Provenance: SHA computed from the GitHub release self-executing jar (ktlint publishes no .sha256
# sidecar; release assets are: ktlint, ktlint.asc, ktlint-1.8.0.zip, ktlint.bat).
set -euo pipefail

KTLINT_VERSION="1.8.0"
KTLINT_SHA256="2efb55a26fab7784a073ceadc06e62d8a0342b21b212127bbafb0da9b26778f4"
BASELINE="config/ktlint/baseline.xml"
REPORT_DIR="app/build/reports/ktlint"

# --- Locate ktlint ---
locate_ktlint() {
    # 1. Repo-cached download (CI puts it here)
    if [ -x ".ktlint/ktlint" ]; then
        local v; v=$(.ktlint/ktlint --version | awk '{print $NF}')
        if [ "$v" = "$KTLINT_VERSION" ]; then echo ".ktlint/ktlint"; return; fi
    fi
    # 2. PATH-installed ktlint whose version matches
    if command -v ktlint >/dev/null 2>&1; then
        local v; v=$(ktlint --version | awk '{print $NF}')
        if [ "$v" = "$KTLINT_VERSION" ]; then echo "ktlint"; return; fi
    fi
    # 3. Not found
    echo ""
}

KTLINT_BIN=$(locate_ktlint)
if [ -z "$KTLINT_BIN" ]; then
    echo "ERROR: ktlint $KTLINT_VERSION not found."
    echo "  Install: brew install ktlint (or download from GitHub releases into .ktlint/ktlint)"
    echo "  Expected SHA-256: $KTLINT_SHA256"
    exit 1
fi

# --- Verify SHA-256 (fail-closed) ---
KTLINT_PATH=$(command -v "$KTLINT_BIN" 2>/dev/null || echo "$KTLINT_BIN")
if [ -f "$KTLINT_PATH" ]; then
    echo "$KTLINT_SHA256  $KTLINT_PATH" | shasum -a 256 -c - >/dev/null 2>&1 || {
        echo "ERROR: SHA-256 mismatch for $KTLINT_PATH — expected $KTLINT_SHA256"
        echo "  The binary may be tampered or a different build. Re-download from GitHub releases."
        exit 1
    }
fi

mkdir -p "$REPORT_DIR"

# --- Run ---
if [ "${1:-}" = "--format" ]; then
    # Format mode: runs -F WITHOUT --baseline (ktlint 1.8.0's --baseline suppresses violations
    # even under -F, so passing it would stop autocorrect from fixing the baselined set).
    echo "ktlint: formatting app/src (no baseline — all violations eligible for fix)..."
    "$KTLINT_BIN" -F "app/src"
else
    # Check mode: WITH --baseline (suppresses existing violations; fails only on NEW).
    echo "ktlint: checking app/src (baseline: $BASELINE)..."
    "$KTLINT_BIN" --baseline="$BASELINE" \
        --reporter=plain \
        --reporter=plain,output="$REPORT_DIR/ktlint.txt" \
        "app/src"
fi

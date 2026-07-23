#!/usr/bin/env bash
# Validate the checked-in gradle-wrapper.jar BEFORE any Gradle invocation (#212 port — GitLab has no
# first-party `gradle/actions/wrapper-validation`). The expected hash is the known-good jar for the
# wrapper's Gradle version: it is the exact jar GitHub's wrapper-validation action already blesses on
# every PR, so it is authoritative. Renovate/the dev must update it on a wrapper (Gradle version) bump.
# Cross-ref: gradle/wrapper/gradle-wrapper.properties pins distributionSha256Sum (verifies the
# downloaded distribution); this pins the wrapper JAR itself (verifies the bootstrap code).
set -euo pipefail
# gradle-wrapper.jar for Gradle 9.6.0 (bump alongside gradle/wrapper/gradle-wrapper.properties).
EXPECTED_WRAPPER_SHA256="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
JAR="gradle/wrapper/gradle-wrapper.jar"
test -f "$JAR" || { echo "Missing $JAR"; exit 1; }
if command -v sha256sum >/dev/null 2>&1; then actual="$(sha256sum "$JAR" | cut -d' ' -f1)"
else actual="$(shasum -a 256 "$JAR" | cut -d' ' -f1)"; fi
[ "$actual" = "$EXPECTED_WRAPPER_SHA256" ] || { echo "gradle-wrapper.jar SHA $actual != expected $EXPECTED_WRAPPER_SHA256"; exit 1; }
echo "gradle-wrapper.jar validated ($actual)"

#!/bin/sh
# Runs the plain-JVM :core module WITHOUT the Android Gradle plugin (cloud containers cannot resolve it).
#   tools/core-harness/run.sh                 gradle :core:test
#   tools/core-harness/run.sh <gradle args>   e.g. :core:test --tests 'castbridge.core.curriculum.*'
# The harness lives in <repo>/.core-harness (git-ignored): :core's build file finds the repository through
# rootProject.projectDir.parentFile, so the harness root must be a direct child of the repository root.
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
H="$ROOT/.core-harness"
mkdir -p "$H"
cat > "$H/settings.gradle.kts" <<'KTS'
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "castbridge-core-harness"
include(":core")
project(":core").projectDir = file("../android/core")
KTS
cat > "$H/build.gradle.kts" <<'KTS'
plugins { kotlin("jvm") version "2.1.0" apply false }
KTS
cp "$ROOT/android/gradle.properties" "$H/gradle.properties" 2>/dev/null || true
if [ $# -eq 0 ]; then set -- :core:test; fi
exec ${GRADLE:-gradle} -p "$H" "$@"

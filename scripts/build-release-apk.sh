#!/usr/bin/env bash
# build-release-apk.sh — build a release APK into release/ for distribution.
#
# Three actions, in order:
#   1. ./scripts/build-release-apk.sh                  # build APK into release/
#   2. ./scripts/build-release-apk.sh --gh-release     # build + upload to GitHub Releases
#   3. ./scripts/build-release-apk.sh --install        # build + adb install onto connected device
#
# Combine: --gh-release --install is fine (the "phone-vibing" happy path).
#
# Output: release/pageboy-<version>-<sha7>.apk and a release/latest.apk symlink.
#
# Signing: uses Gradle's debug keystore by default (good for personal
# sideload via Obtainium). For production signing, set
# PAGEBOY_RELEASE_KEYSTORE + PAGEBOY_RELEASE_KEY_ALIAS +
# PAGEBOY_RELEASE_KEY_PASSWORD env vars.

set -euo pipefail

ROOT="$(dirname "$(readlink -f "$0")")/.."
cd "${ROOT}"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

PUSH_TO_GH=false
INSTALL_TO_DEVICE=false
for arg in "$@"; do
    case "$arg" in
        --gh-release|--github)   PUSH_TO_GH=true ;;
        --install|--adb-install) INSTALL_TO_DEVICE=true ;;
        --help|-h)
            sed -n '1,/^$/p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *) echo "unknown flag: $arg" >&2; exit 1 ;;
    esac
done

BUILD_OK=false
GH_RELEASE_OK=false
INSTALL_OK=false

VERSION="$(awk -F'"' '/versionName/ {print $2; exit}' app/build.gradle.kts 2>/dev/null \
    || awk -F'=' '/versionName/ {print $2; exit}' app/build.gradle.kts \
    | tr -d ' "' || true)"
[ -z "${VERSION}" ] && VERSION="0.1.0-dev"
SHA7="$(git rev-parse --short=7 HEAD)"
SHA_FULL="$(git rev-parse HEAD)"
TAG="${VERSION}-${SHA7}"
REL_TAG="v${TAG}"

OUT_DIR="${ROOT}/release"
OUT_APK="${OUT_DIR}/pageboy-${TAG}.apk"
mkdir -p "${OUT_DIR}"

if [ -n "${PAGEBOY_RELEASE_KEYSTORE:-}" ]; then
    echo "[build-release-apk] release-signed build"
    GRADLE_TASK="assembleRelease"
    BUILD_APK="app/build/outputs/apk/release/pageboy-release.apk"
else
    echo "[build-release-apk] debug-signed build (set PAGEBOY_RELEASE_KEYSTORE for production signing)"
    GRADLE_TASK="assembleDebug"
    BUILD_APK="app/build/outputs/apk/debug/pageboy-debug.apk"
fi

echo "[build-release-apk] running ./gradlew :app:${GRADLE_TASK}..."
./gradlew ":app:${GRADLE_TASK}" --console=plain >/dev/null

if [ ! -f "${BUILD_APK}" ]; then
    echo "[build-release-apk] expected APK at ${BUILD_APK} but it's missing" >&2
    exit 1
fi

cp "${BUILD_APK}" "${OUT_APK}"
ln -sf "$(basename "${OUT_APK}")" "${OUT_DIR}/latest.apk"
APK_SIZE="$(du -h "${OUT_APK}" | cut -f1)"
APK_SHA256="$(sha256sum "${OUT_APK}" | awk '{print $1}')"
echo "[build-release-apk] ${OUT_APK} (${APK_SIZE})"
echo "[build-release-apk] sha256: ${APK_SHA256}"
echo "[build-release-apk] symlink: ${OUT_DIR}/latest.apk"
BUILD_OK=true

if "${PUSH_TO_GH}"; then
    if ! command -v gh >/dev/null; then
        echo "[build-release-apk] gh CLI not found" >&2; exit 1
    fi

    PREV_TAG="$(git tag -l 'v*' --sort=-creatordate | grep -v "^${REL_TAG}\$" | head -n1 || true)"
    if [ -n "${PREV_TAG}" ]; then
        COMMIT_RANGE="${PREV_TAG}..HEAD"
        CHANGELOG_HEADER="Changes since \`${PREV_TAG}\`"
    else
        COMMIT_RANGE="HEAD"
        CHANGELOG_HEADER="Recent changes"
    fi
    COMMIT_LIST="$(git log "${COMMIT_RANGE}" --pretty=format:'- %s (%h)' | head -n 50)"
    [ -z "${COMMIT_LIST}" ] && COMMIT_LIST="- (no commits since previous tag)"

    NOTES_FILE="$(mktemp)"
    cat >"${NOTES_FILE}" <<EOF
Auto-built APK from commit \`${SHA7}\`.

## Install

Sideload via [Obtainium](https://github.com/ImranR98/Obtainium) or install directly:

\`\`\`
adb install -r pageboy-${TAG}.apk
\`\`\`

## ${CHANGELOG_HEADER}

${COMMIT_LIST}

## Verify build

| Field | Value |
| --- | --- |
| Commit | \`${SHA_FULL}\` |
| APK | \`pageboy-${TAG}.apk\` |
| APK SHA-256 | \`${APK_SHA256}\` |
| Build flavour | \`${GRADLE_TASK}\` |
EOF

    if gh release view "${REL_TAG}" >/dev/null 2>&1; then
        gh release upload "${REL_TAG}" "${OUT_APK}" --clobber
        gh release edit "${REL_TAG}" --notes-file "${NOTES_FILE}" >/dev/null
    else
        gh release create "${REL_TAG}" "${OUT_APK}" \
            --title "pageboy ${VERSION} (${SHA7})" \
            --notes-file "${NOTES_FILE}"
    fi
    rm -f "${NOTES_FILE}"

    REL_URL="$(gh release view "${REL_TAG}" --json url -q .url)"
    echo "[build-release-apk] ${REL_URL}"

    if git rev-parse -q --verify "refs/tags/${REL_TAG}" >/dev/null; then
        git push origin "refs/tags/${REL_TAG}" || \
            echo "[build-release-apk] tag push failed (continuing)"
    else
        git fetch origin "refs/tags/${REL_TAG}:refs/tags/${REL_TAG}" 2>/dev/null || true
    fi
    GH_RELEASE_OK=true
fi

if "${INSTALL_TO_DEVICE}"; then
    if ! command -v adb >/dev/null; then
        echo "[build-release-apk] adb not on PATH" >&2; exit 1
    fi
    if ! adb devices | grep -qE '\<device$'; then
        echo "[build-release-apk] no ADB device connected" >&2; exit 1
    fi
    echo "[build-release-apk] adb install -r ${OUT_APK}..."
    adb install -r "${OUT_APK}"
    INSTALL_OK=true
fi

echo "[build-release-apk] ----- summary -----"
echo "[build-release-apk] build:       $($BUILD_OK && echo OK || echo FAIL)"
if "${PUSH_TO_GH}"; then
    echo "[build-release-apk] gh release: $($GH_RELEASE_OK && echo OK || echo FAIL)"
fi
if "${INSTALL_TO_DEVICE}"; then
    echo "[build-release-apk] install:    $($INSTALL_OK && echo OK || echo FAIL)"
fi
echo "[build-release-apk] done"

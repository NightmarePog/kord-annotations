#!/usr/bin/env bash
set -euo pipefail

exec ./gradlew "$@" \
    -PreleaseVersion="$RELEASE_VERSION" \
    -PretryPluginPortalOnly="$RETRY_PLUGIN_PORTAL_ONLY" \
    --no-daemon

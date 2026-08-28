#!/usr/bin/env bash
set -euo pipefail

release_tag="v$RELEASE_VERSION"
if git rev-parse --verify "refs/tags/$release_tag" >/dev/null 2>&1; then
    [[ "$(git rev-list -n 1 "$release_tag")" == "$(git rev-parse HEAD)" ]] || {
        echo "Tag $release_tag exists on a different commit." >&2
        exit 1
    }
    tag_exists=true
else
    tag_exists=false
fi

printf 'tag_exists=%s\n' "$tag_exists" >> "$GITHUB_OUTPUT"

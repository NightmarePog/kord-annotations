#!/usr/bin/env bash
set -euo pipefail

release_tag="v$RELEASE_VERSION"
archive="build/release/kord-annotations-$RELEASE_VERSION-maven-repository.tar.gz"

if [[ "$TAG_EXISTS" == "false" ]]; then
    git config user.name "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
    git tag -a "$release_tag" -m "Release $release_tag"
    git push origin "$release_tag"
fi

if gh release view "$release_tag" >/dev/null 2>&1; then
    gh release upload "$release_tag" "$archive" "$archive.sha256" --clobber
else
    gh release create "$release_tag" "$archive" "$archive.sha256" \
        --verify-tag --generate-notes --title "$release_tag"
fi

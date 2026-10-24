#!/bin/sh
# Sync vendored skills in .agents/skills/ from upstream at a pinned commit.
# Usage: scripts/sync-skills.sh            re-sync at the SHA in skills.lock
#        scripts/sync-skills.sh latest     bump to upstream HEAD
#        scripts/sync-skills.sh <sha>      pin to a specific commit
set -eu

root=$(git rev-parse --show-toplevel)
dest="$root/.agents/skills"
lock="$dest/skills.lock"
. "$lock"

target=${1:-$sha}
if [ "$target" = latest ]; then
  target=$(git ls-remote "$repo" HEAD | cut -f1)
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git -C "$tmp" init -q
git -C "$tmp" fetch -q --depth 1 "$repo" "$target"
git -C "$tmp" checkout -q FETCH_HEAD

for p in $paths; do
  name=$(basename "$p")
  rm -rf "${dest:?}/$name"
  cp -R "$tmp/$p" "$dest/$name"
done
cp "$tmp/LICENSE" "$dest/LICENSE"

{
  echo "# Source of the vendored skills; managed by scripts/sync-skills.sh."
  echo "repo=$repo"
  echo "sha=$target"
  echo "paths=\"$paths\""
} > "$lock"

if [ -z "$(git -C "$root" status --porcelain -- .agents/skills)" ]; then
  echo "Skills already up to date at $target."
else
  echo "Skills synced to $target:"
  git -C "$root" status --short -- .agents/skills
fi

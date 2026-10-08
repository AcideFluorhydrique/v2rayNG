#!/bin/bash
# Publishes the generated F-Droid repository to a mirror on another git host.
#
#   publish-mirror.sh <git url> <user name> [file to add as .gitlab-ci.yml]
#
# The mirror serves a branch as a static site, so the repository directory
# (SOURCE_DIR) is pushed there as fdroid/repo: fdroidserver only accepts a
# mirror whose address ends in "fdroid", and appends "/repo" to it itself.
# Every push is a single commit that replaces the branch, like gh-pages, so
# the mirror holds no history and stays the size of the APKs it offers.
#
# The token is read from MIRROR_TOKEN and handed to git through a credential
# helper, so it never appears in a URL, a command line or the log.

set -o errexit
set -o pipefail
set -o nounset

url="${1:?git url}"
export MIRROR_USER="${2:?user name}"
ci_file="${3:-}"
SOURCE_DIR="${SOURCE_DIR:-fdroid-repo/repo}"
BRANCH="${BRANCH:-pages}"

if [[ -z "${MIRROR_TOKEN:-}" ]]; then
    echo "No token configured for $url." >&2
    exit 1
fi
if [[ ! -f "$SOURCE_DIR/index-v1.jar" ]]; then
    echo "$SOURCE_DIR holds no signed index; refusing to publish it." >&2
    exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/fdroid"
cp -R "$SOURCE_DIR" "$work/fdroid/repo"
if [[ -n "$ci_file" ]]; then
    cp "$ci_file" "$work/.gitlab-ci.yml"
fi

git -C "$work" init --quiet --initial-branch "$BRANCH"
git -C "$work" add --all
git -C "$work" \
    -c user.name='github-actions[bot]' \
    -c user.email='41898282+github-actions[bot]@users.noreply.github.com' \
    commit --quiet --message "deploy: ${GITHUB_SHA:-manual}"

# The first, empty helper drops any helper the runner has configured.
GIT_TERMINAL_PROMPT=0 git -C "$work" \
    -c credential.helper= \
    -c credential.helper='!f() { test "$1" = get && printf "username=%s\npassword=%s\n" "$MIRROR_USER" "$MIRROR_TOKEN"; }; f' \
    push --force "$url" "HEAD:refs/heads/$BRANCH"

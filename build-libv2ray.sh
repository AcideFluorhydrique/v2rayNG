#!/bin/bash
# Builds libv2ray.aar (the Xray core and its JNI bindings) from the
# AndroidLibXrayLite submodule, with the routing databases from the
# forkray-geodata submodule in its assets.
#
#   build-libv2ray.sh <output .aar path>
#
# Both .github/workflows/fdroid-source-build.yml and the F-Droid recipe run
# this script, so that the two builds cannot drift apart; see
# docs/reproducible-build.md. It needs the pinned Go, JDK and NDK already set
# up: go and java on PATH, ANDROID_HOME and ANDROID_NDK_HOME set.

set -o errexit
set -o pipefail
set -o nounset

# Must equal minSdk in V2rayNG/app/build.gradle.kts.
ANDROID_API=24

out="${1:?output .aar path}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$(dirname "$out")"
out="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"

cd "$root/AndroidLibXrayLite"
echo "go.mod requires go $(awk '/^go /{print $2; exit}' go.mod); using $(go version)"

# Geo data. These .dat databases end up in the aar's assets and so in the APK.
# Upstream's gen_assets.sh downloads prebuilt ones, and the repository commits
# two more; neither is used. They are built from the plain-text sources in the
# forkray-geodata submodule, whose build is byte-reproducible from its commit
# and fails if a category the routing presets need is missing.
bash "$root/forkray-geodata/scripts/build.sh"
rm -f assets/*.dat
mkdir -p assets
cp "$root"/forkray-geodata/out/*.dat assets/
ls -l assets

# gomobile, at the golang.org/x/mobile revision this module already depends on
# so that the generated binding code matches go.sum, with one patch: see
# patches/gomobile-build-in-module.patch.
mobile_version="$(go list -m -f '{{.Version}}' golang.org/x/mobile)"
echo "gomobile version: ${mobile_version} + patches/gomobile-build-in-module.patch"
go mod download golang.org/x/mobile
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -R "$(go list -m -f '{{.Dir}}' golang.org/x/mobile)" "$work/mobile"
chmod -R u+w "$work/mobile"
(cd "$work/mobile" && git apply "$root/patches/gomobile-build-in-module.patch")
(cd "$work/mobile" && GOBIN="$work/bin" go install ./cmd/gomobile)
export PATH="$work/bin:$PATH"

gomobile init

# gomobile bind runs gobind out of this module, so the module graph has to
# cover golang.org/x/mobile/cmd/gobind; `go mod tidy` is what makes that true.
# It resolves minimum versions and does not upgrade anything, but any change it
# makes means the committed go.mod/go.sum were incomplete, so say so.
go mod tidy
if ! git diff --quiet -- go.mod go.sum; then
    # GitHub Actions shows ::warning:: lines as annotations on the run.
    if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
        echo '::warning::go mod tidy modified go.mod/go.sum'
    else
        echo 'warning: go mod tidy modified go.mod/go.sum'
    fi
    git diff --stat -- go.mod go.sum
fi
go mod download

# -buildvcs=false: the patched gomobile builds inside this module, which is a
# git checkout, so Go would otherwise stamp its revision and whether the
# working tree is modified (it is: assets/ was just replaced) into the binary.
GOFLAGS=-buildvcs=false gomobile bind -v \
    -target=android/arm,android/arm64,android/386,android/amd64 \
    -androidapi "$ANDROID_API" \
    -trimpath \
    -ldflags='-s -w -buildid= -checklinkname=0' \
    -o "$out" \
    ./
rm -rf build

# The point of the patch: no library may contain the directory it was built
# in, or nobody building elsewhere can reproduce it.
for so in $(unzip -Z1 "$out" 'jni/*/libgojni.so'); do
    unzip -p "$out" "$so" > "$work/lib.so"
    if grep -aqF "$root" "$work/lib.so"; then
        echo "error: $so contains the build directory $root" >&2
        exit 1
    fi
done
ls -l "$out"

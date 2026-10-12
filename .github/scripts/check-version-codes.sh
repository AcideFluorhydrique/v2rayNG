#!/bin/bash
# Checks the version codes of the per-ABI F-Droid APKs before they can be
# published, by reading them back from the built files.
#
# An F-Droid client offers the highest version code a device can run. A 64-bit
# device also runs the 32-bit build, so:
#   - each 64-bit APK must rank above its 32-bit counterpart, or the client
#     installs the 32-bit build on 64-bit devices (as 2.3.9 and 2.3.10 did);
#   - every APK of a new version must rank above every APK already published,
#     or some device is never offered the update.
# A version code cannot be corrected once its APK is out, so this runs in the
# build, which a release goes through before anything is uploaded.
#
# Usage: check-version-codes.sh <directory with the APKs> [published index-v2.json URL]
# Needs aapt2 on PATH or in AAPT2. Without the URL only the order within this
# build is checked. With PUBLISHED_CHECK=warn a code
# that is not above the published ones, or an index that cannot be fetched, is
# reported without failing, for builds that are not releases: rebuilding an
# older commit is legitimate there.

set -o errexit
set -o pipefail
set -o nounset

apk_dir="$1"
index_url="${2:-}"
aapt2="${AAPT2:-aapt2}"
published_check="${PUBLISHED_CHECK:-fail}"

declare -A code
version_name=""
for apk in "$apk_dir"/*.apk; do
    badging=$("$aapt2" dump badging "$apk")
    package_line=$(grep -m 1 '^package: ' <<<"$badging")
    vc=$(sed -nE "s/.* versionCode='([0-9]+)'.*/\1/p" <<<"$package_line")
    vn=$(sed -nE "s/.* versionName='([^']+)'.*/\1/p" <<<"$package_line")
    native=$(sed -nE "s/^native-code: //p" <<<"$badging" | tr -d "'")
    if [[ -z "$vc" || -z "$vn" ]]; then
        echo "::error::Could not read the version of $(basename "$apk")."
        exit 1
    fi
    # The universal APK carries every ABI and is not offered by the repository.
    if [[ "$native" == *" "* ]]; then
        echo "$(basename "$apk"): ${vn} (${vc}), universal, not ranked"
        continue
    fi
    if [[ -n "${code[$native]:-}" ]]; then
        echo "::error::Two APKs are built for ${native}."
        exit 1
    fi
    if [[ -n "$version_name" && "$vn" != "$version_name" ]]; then
        echo "::error::The APKs disagree on the version name: ${version_name} and ${vn}."
        exit 1
    fi
    version_name="$vn"
    code[$native]="$vc"
    echo "$(basename "$apk"): ${vn} (${vc}), ${native}"
done

failed=0
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    if [[ -z "${code[$abi]:-}" ]]; then
        echo "::error::No APK was built for ${abi}."
        failed=1
    fi
done
(( failed == 0 )) || exit 1

for pair in "arm64-v8a armeabi-v7a" "x86_64 x86"; do
    read -r wide narrow <<<"$pair"
    if (( code[$wide] <= code[$narrow] )); then
        echo "::error::${wide} (${code[$wide]}) does not rank above ${narrow} (${code[$narrow]}): F-Droid clients would install the 32-bit build on 64-bit devices."
        failed=1
    fi
done
# x86 devices often run ARM code through translation, so the native builds
# must rank above the ARM ones there too.
if (( code[x86] <= code[arm64-v8a] )); then
    echo "::error::x86 (${code[x86]}) does not rank above arm64-v8a (${code[arm64-v8a]}): x86 devices that translate ARM code would be offered an ARM build."
    failed=1
fi
(( failed == 0 )) || exit 1
echo "Order within ${version_name}: armeabi-v7a < arm64-v8a < x86 < x86_64, as required."

if [[ -z "$index_url" ]]; then
    exit 0
fi
index=$(mktemp)
if ! curl -fsSL --retry 2 -o "$index" "$index_url"; then
    if [[ "$published_check" == "warn" ]]; then
        echo "::warning::Could not fetch ${index_url}; the codes were not compared with the published ones."
        exit 0
    fi
    # Nothing is uploaded yet at this point of a release, so retrying costs
    # nothing, while publishing unchecked codes cannot be undone.
    echo "::error::Could not fetch ${index_url}, so the codes cannot be compared with the published ones. Retry the run."
    exit 1
fi
lowest=$(printf '%s\n' "${code[@]}" | sort -n | head -n 1)
# Published APKs of other versions; this version's own are the same codes when
# a commit that is already released gets built again.
if ! python3 - "$index" "$version_name" "$lowest" "$published_check" <<'PY'
import json
import sys

index_path, version_name, lowest = sys.argv[1], sys.argv[2], int(sys.argv[3])
level = 'warning' if sys.argv[4] == 'warn' else 'error'
with open(index_path, encoding='utf-8') as fp:
    index = json.load(fp)
published = [
    (version['manifest']['versionCode'], version['manifest']['versionName'], version['file']['name'])
    for package in index['packages'].values()
    for version in package['versions'].values()
    if version['manifest']['versionName'] != version_name
]
blocking = sorted(p for p in published if p[0] >= lowest)
for vc, vn, name in blocking:
    print(f"::{level}::Published {name} ({vn}, {vc}) is not below this build's lowest code {lowest}: "
          "some devices would not be offered the update.")
if blocking:
    sys.exit(1 if level == 'error' else 0)
highest = max((p[0] for p in published), default=None)
print(f"Above every published APK of another version (highest published: {highest}).")
PY
then
    exit 1
fi

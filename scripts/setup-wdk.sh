#!/usr/bin/env bash
set -euo pipefail

wdk_revision=79153a0351a796667bc5a7cc2fd3ba55741e7462
project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
wdk_dir="$project_root/vendor/wdk-core-kotlin"

if [[ ! -d "$wdk_dir/.git" ]]; then
    mkdir -p "$wdk_dir"
    git -C "$wdk_dir" init
    git -C "$wdk_dir" remote add origin https://github.com/tetherto/wdk-core-kotlin.git
fi

if [[ "$(git -C "$wdk_dir" rev-parse HEAD 2>/dev/null || true)" != "$wdk_revision" ]]; then
    git -C "$wdk_dir" fetch --depth 1 origin "$wdk_revision"
    git -C "$wdk_dir" checkout --detach FETCH_HEAD
fi

if [[ "$(git -C "$wdk_dir" rev-parse HEAD)" != "$wdk_revision" ]]; then
    echo "WDK checkout did not match the pinned revision" >&2
    exit 1
fi

# The upstream checkout points to the bundler's Git branch. npm 12 blocks
# Git dependencies by default, and that Git package lacks compiled dist files.
# Use the published, built release for this app's reproducible local checkout.
python3 - "$wdk_dir/js/package.json" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
package = json.loads(path.read_text())
expected = 'github:tetherto/wdk-worklet-bundler#main'
current = package['devDependencies']['@tetherto/wdk-worklet-bundler']
if current not in (expected, '1.0.0-beta.14'):
    raise SystemExit(f'Unexpected worklet bundler dependency: {current}')
package['devDependencies']['@tetherto/wdk-worklet-bundler'] = '1.0.0-beta.14'
package['scripts'].pop('postinstall', None)
path.write_text(json.dumps(package, indent=2) + '\n')
PY
cp "$project_root/scripts/patch-wdk-bundler.js" "$wdk_dir/js/patch-wdk-bundler.js"
cp "$project_root/scripts/wdk.config.js" "$wdk_dir/js/wdk.config.js"

if [[ -f "$wdk_dir/js/package-lock.json" ]] &&
   grep -q 'git+ssh://git@github.com/tetherto/wdk-worklet-bundler' "$wdk_dir/js/package-lock.json"; then
    rm -rf "$wdk_dir/js/node_modules" "$wdk_dir/js/package-lock.json"
fi
cp "$project_root/gradle/wdk-js-package-lock.json" "$wdk_dir/js/package-lock.json"

echo "WDK Kotlin ready: $wdk_revision"

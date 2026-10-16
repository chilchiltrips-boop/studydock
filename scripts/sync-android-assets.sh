#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$root/android/app/src/main/assets"
cp -a "$root/app/." "$root/android/app/src/main/assets/"
echo 'Android assets synchronized.'

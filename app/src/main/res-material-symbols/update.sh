#!/usr/bin/env bash
#
# Re-fetch the Material Symbols vector drawables in drawable/ from upstream.
# See README.md for provenance. Usage: ./update.sh [<commit-ish>]

set -euo pipefail

cd "$(dirname "$0")"

REV="${1:-49d4db35df873165d6bd6ba09b063c7dafbac2f4}"
BASE="https://raw.githubusercontent.com/google/material-design-icons/$REV/symbols/android"

# Upstream path (under $BASE) -> local file name in drawable/.
ICONS=(
  "arrow_back/materialsymbolsoutlined/arrow_back_24px.xml arrow_back_24px.xml"
  "close/materialsymbolsoutlined/close_24px.xml close_24px.xml"
  "search/materialsymbolsoutlined/search_24px.xml search_24px.xml"
)

for entry in "${ICONS[@]}"; do
  read -r src dst <<<"$entry"
  # Upstream tints with ?attr/colorControlNormal, an AppCompat attribute this
  # app doesn't have; drop it (Compose's Icon() applies its own tint).
  curl -fsSL "$BASE/$src" | perl -0pe 's/\s+android:tint="\?attr\/colorControlNormal"//' > "drawable/$dst"
  echo "drawable/$dst <- $src @ $REV"
done

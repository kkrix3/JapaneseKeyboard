#!/usr/bin/env bash
# Run production Kotlin on a JDK 17+ host with an installed kotlinc.
set -euo pipefail
task_root=$(cd -- "$(dirname -- "$0")/../.." && pwd)
output_dir=${1:-"$task_root/tools/emoji_search/results"}
compiler=${KOTLINC_BIN:-kotlinc}
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
sources="$task_root/symbol_keyboard/src/main/java/com/kazumaproject/emoji/search"
"$compiler" "$sources/EmojiSearchNormalizer.kt" "$sources/EmojiSearchRepository.kt" \
  "$sources/EmojiSearchEngine.kt" "$sources/EmojiSearchInputState.kt" \
  "$task_root/tools/emoji_search/Evaluate.kt" -include-runtime -d "$work_dir/evaluate.jar"
java -jar "$work_dir/evaluate.jar" "$task_root" "$output_dir"

#!/usr/bin/env bash
set -euo pipefail
compiler_lib_dir="${1:?Gradle 9.6.0 lib directory}"
output_dir="${2:?Output directory}"
repo_dir="$(git rev-parse --show-toplevel)"
bench_dir="$repo_dir/tools/prediction-efficiency"
source_dir="$repo_dir/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter"
mkdir -p "$output_dir"
classpath="$compiler_lib_dir/kotlin-stdlib-2.3.21.jar:$compiler_lib_dir/kotlinx-coroutines-core-jvm-1.10.2.jar"
sources=("$source_dir"/bitset/*.kt "$source_dir/connection_id/Extensions.kt"
  "$source_dir/compact/PackedArray.kt" "$source_dir/louds/LoudsTermIterator.kt"
  "$source_dir/louds/LOUDS.kt" "$source_dir/louds/with_term_id/LOUDSWithTermId.kt"
  "$source_dir/dictionary/TokenArray.kt" "$source_dir"/dictionary/models/*.kt
  "$source_dir/graph/KanaFlickLayout.kt" "$source_dir/graph/TypoCandidate.kt"
  "$source_dir/graph/TypoCorrectionResult.kt" "$source_dir/graph/OmissionSearchResult.kt"
  "$source_dir/engine/PredictionConfig.kt" "$source_dir/engine/TripleDictionaryData.kt"
  "$source_dir/engine/DictionaryPredictionSearch.kt" "$source_dir/candidate/Candidate.kt"
  "$source_dir/candidate/CandidateConversionSegment.kt")
java -cp "$compiler_lib_dir/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
  -classpath "$classpath" -d "$output_dir/benchmark.jar" "$bench_dir"/standalone/*.kt "${sources[@]}"
for run in 1 2 3; do
  java -Xms512m -Xmx1024m -XX:+UseSerialGC -cp "$output_dir/benchmark.jar:$classpath" \
    PredictionBenchmarkKt "$repo_dir/app/src/main/assets" "$run" > "$output_dir/run$run.tsv"
done

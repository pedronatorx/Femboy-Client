#!/usr/bin/env bash
# Measures Femboy Client's own CPU time and allocations in a fixed scene (see BenchmarkTest.java).
#
# Runs the client game test in benchmark mode in a virtual X display (Xvfb + Mesa llvmpipe), or on your
# real display with --visible. Frame times depend on the renderer; module times are the comparable numbers.
#
# Result: build/run/clientGameTest/benchmark.txt, copied to build-logs/benchmark-<timestamp>.txt
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" ]]; then
	echo "JAVA_HOME is not set. Point it at a JDK 25+ (needed to run Gradle/Loom), e.g.:" >&2
	echo "  export JAVA_HOME=\$HOME/.local/share/jdks/jdk-25.0.4.1+1" >&2
	exit 2
fi

mkdir -p build-logs
stamp=$(date +%Y%m%d-%H%M%S)
log="build-logs/benchmark-$stamp.log"
rm -rf build/run/clientGameTest/config/femboyclient
export FEMBOY_BENCHMARK=1
if [[ "${1:-}" == "--visible" ]]; then
	./gradlew runClientGameTest --stacktrace 2>&1 | tee "$log"
else
	command -v xvfb-run >/dev/null || { echo "xvfb-run not found; install Xvfb or use --visible" >&2; exit 2; }
	env -u WAYLAND_DISPLAY xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest --stacktrace 2>&1 | tee "$log"
fi

status=${PIPESTATUS[0]}
if [[ -f build/run/clientGameTest/benchmark.txt ]]; then
	cp build/run/clientGameTest/benchmark.txt "build-logs/benchmark-$stamp.txt"
	echo
	cat build/run/clientGameTest/benchmark.txt
fi
exit "$status"

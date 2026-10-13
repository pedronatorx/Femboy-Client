#!/usr/bin/env bash
# Runs the NOVA client game tests in a real Minecraft client.
#
# By default the client renders into a virtual X display (Xvfb + Mesa llvmpipe), so nothing
# appears on your desktop. Pass --visible to use your real display instead.
#
# Results: build/run/clientGameTest/nova-test-report.txt, screenshots in .../screenshots,
# full log in build-logs/client-gametest-<timestamp>.log
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" ]]; then
	echo "JAVA_HOME is not set. Point it at a JDK 25+ (needed to run Gradle/Loom), e.g.:" >&2
	echo "  export JAVA_HOME=\$HOME/.local/share/jdks/jdk-25.0.4.1+1" >&2
	exit 2
fi

mkdir -p build-logs
log="build-logs/client-gametest-$(date +%Y%m%d-%H%M%S).log"

if [[ "${1:-}" == "--visible" ]]; then
	./gradlew runClientGameTest --stacktrace 2>&1 | tee "$log"
else
	command -v xvfb-run >/dev/null || { echo "xvfb-run not found; install Xvfb or use --visible" >&2; exit 2; }
	# Unset WAYLAND_DISPLAY so GLFW uses the virtual X server rather than the desktop compositor.
	env -u WAYLAND_DISPLAY xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest --stacktrace 2>&1 | tee "$log"
fi

status=${PIPESTATUS[0]}
echo
echo "Log: $log"
[[ -f build/run/clientGameTest/nova-test-report.txt ]] && cat build/run/clientGameTest/nova-test-report.txt
exit "$status"

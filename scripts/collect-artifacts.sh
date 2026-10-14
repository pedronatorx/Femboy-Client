#!/usr/bin/env bash
# Copies the latest test evidence into docs/ so it can be committed:
#   docs/screenshots/   screenshots taken by the client game test
#   docs/test-reports/  client check list, unit test summary, natural-world report
#   docs/research/      headless research evaluation (from ./gradlew runGameTest)
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p docs/screenshots docs/test-reports docs/research
if [[ -d build/run/clientGameTest/screenshots ]]; then
	rm -f docs/screenshots/*.png
	cp build/run/clientGameTest/screenshots/*.png docs/screenshots/
fi
[[ -f build/run/clientGameTest/test-report.txt ]] && cp build/run/clientGameTest/test-report.txt docs/test-reports/client-game-test-report.txt
[[ -f build/run/clientGameTest/femboyclient-research/natural-world.md ]] && cp build/run/clientGameTest/femboyclient-research/natural-world.md docs/research/natural-world.md
if [[ -d build/run/gameTest/femboyclient-research ]]; then
	cp build/run/gameTest/femboyclient-research/evaluation.md build/run/gameTest/femboyclient-research/evaluation-including-incomplete.md \
		build/run/gameTest/femboyclient-research/evaluation-summary.json build/run/gameTest/femboyclient-research/dataset.csv docs/research/
fi
if [[ -d build/test-results/test ]]; then
	python3 - <<'PY' > docs/test-reports/unit-tests.txt
import glob, xml.etree.ElementTree as ET
total = fail = 0
for f in sorted(glob.glob('build/test-results/test/*.xml')):
    s = ET.parse(f).getroot()
    t, fl = int(s.get('tests')), int(s.get('failures')) + int(s.get('errors'))
    total += t; fail += fl
    print(f"{s.get('name')}: {t} tests, {fl} failed")
print(f"TOTAL: {total} tests, {fail} failed")
PY
fi
echo "Artifacts copied into docs/"

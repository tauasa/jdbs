#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════════
#  start-jdbs.sh – JDBS Server launcher for Linux / macOS
#
#  Usage:
#    ./start-jdbs.sh                     # GUI mode, defaults
#    ./start-jdbs.sh --cli               # Headless CLI mode
#    ./start-jdbs.sh --port=7000         # Custom port
#    ./start-jdbs.sh --max-clients=25    # Override max clients
#    ./start-jdbs.sh --cli --port=7000   # CLI + custom port
# ════════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Resolve script directory (works with symlinks) ────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Locate the fat jar ────────────────────────────────────────────────────────
FAT_JAR="$(find "$SCRIPT_DIR" -maxdepth 3 \
    -name "jdbs-server-*-fat.jar" \
    ! -name "*-sources*" \
    | sort -V | tail -n1)"

if [[ -z "$FAT_JAR" ]]; then
    echo "ERROR: jdbs-server fat jar not found."
    echo "       Run 'mvn package' from the project root first."
    echo "       Expected: jdbs-server/target/jdbs-server-<version>-fat.jar"
    exit 1
fi

echo "JDBS Server"
echo "  Jar:  $FAT_JAR"
echo "  Args: $*"
echo ""

# ── Java version check (17+) ──────────────────────────────────────────────────
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | head -1 | sed 's/[^0-9.]//g' | cut -d. -f1)

if [[ "$JAVA_VERSION" -lt 17 ]]; then
    echo "ERROR: Java 17 or later is required (found Java $JAVA_VERSION)."
    exit 1
fi

# ── JVM flags ─────────────────────────────────────────────────────────────────
JVM_OPTS=(
    # Heap: 64 MB initial, 512 MB max – adjust for large log volumes
    "-Xms64m"
    "-Xmx512m"

    # Logback external config (optional – override with your own logback.xml)
    # "-Dlogback.configurationFile=/path/to/my-logback.xml"

    # Root log level override (TRACE|DEBUG|INFO|WARN|ERROR)
    # "-Dlogging.level.root=DEBUG"
)

# Detect GUI vs CLI mode to set headless flag
if [[ "$*" == *"--cli"* ]]; then
    JVM_OPTS+=("-Djava.awt.headless=true")
fi

# ── Launch ────────────────────────────────────────────────────────────────────
exec "$JAVA_BIN" "${JVM_OPTS[@]}" -jar "$FAT_JAR" "$@"

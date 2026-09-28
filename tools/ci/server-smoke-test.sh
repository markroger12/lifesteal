#!/usr/bin/env bash
# Boots a real server with LifeCore installed, runs console commands, restarts it and
# fails if the plugin logged an error.
#
# Usage: server-smoke-test.sh <paper|folia|purpur|spigot> <version-spec> <plugin-jar>
#   For spigot, SPIGOT_JAR must point to a jar built with BuildTools.
# Environment: JAVA_HOME_21_X64 and JAVA_HOME_25_X64 (set by actions/setup-java).
set -euo pipefail

PLATFORM="$1"
SPEC="$2"
PLUGIN_JAR="$(realpath "$3")"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${WORK_DIR:-$ROOT/server-test}"
USER_AGENT="LifeCore-CI/1.0 (https://github.com/markroger12/lifesteal)"

fail() {
    echo "::error::$*"
    exit 1
}

rm -rf "$WORK"
mkdir -p "$WORK/plugins"
cd "$WORK"

if [ "$PLATFORM" = "spigot" ]; then
    VERSION="$SPEC"
    [ -f "${SPIGOT_JAR:-}" ] || fail "SPIGOT_JAR is not set or missing"
    cp "$SPIGOT_JAR" server.jar
else
    RESOLVED="$(python3 "$ROOT/tools/ci/resolve_server.py" "$PLATFORM" "$SPEC")" \
        || fail "could not resolve a $PLATFORM server for '$SPEC'"
    read -r VERSION URL <<< "$RESOLVED"
    [ -n "${URL:-}" ] || fail "resolver returned no download URL for $PLATFORM $SPEC"
    echo "Downloading $PLATFORM $VERSION from $URL"
    curl -fsSL -A "$USER_AGENT" -o server.jar "$URL"
fi

case "$VERSION" in
    1.*) JAVA_BIN="${JAVA_HOME_21_X64:?}/bin/java" ;;
    *) JAVA_BIN="${JAVA_HOME_25_X64:?}/bin/java" ;;
esac
echo "Testing LifeCore on $PLATFORM $VERSION with $("$JAVA_BIN" -version 2>&1 | head -1)"
echo "platform=$PLATFORM" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "version=$VERSION" >> "${GITHUB_OUTPUT:-/dev/null}"

cp "$PLUGIN_JAR" plugins/
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
view-distance=3
simulation-distance=3
spawn-protection=0
max-players=5
PROPS

SERVER_PID=""

wait_for() { # <pattern> <timeout seconds> <log>
    local pattern="$1" timeout="$2" log="$3" waited=0
    until grep -q -- "$pattern" "$log" 2>/dev/null; do
        if [ -n "$SERVER_PID" ] && ! kill -0 "$SERVER_PID" 2>/dev/null; then
            tail -n 60 "$log" || true
            fail "server exited while waiting for '$pattern'"
        fi
        if [ "$waited" -ge "$timeout" ]; then
            tail -n 60 "$log" || true
            fail "timed out after ${timeout}s waiting for '$pattern'"
        fi
        sleep 1
        waited=$((waited + 1))
    done
}

send() {
    echo "> $1"
    echo "$1" >&3
}

start_server() { # <log>
    rm -f console.in
    mkfifo console.in
    exec 3<>console.in
    "$JAVA_BIN" -Xms1G -Xmx2G -Dcom.mojang.eula.agree=true -jar server.jar --nogui < console.in > "$1" 2>&1 &
    SERVER_PID=$!
    wait_for 'Done (' 600 "$1"
}

stop_server() { # <log>
    send "stop"
    local waited=0
    while kill -0 "$SERVER_PID" 2>/dev/null; do
        if [ "$waited" -ge 180 ]; then
            kill -9 "$SERVER_PID" || true
            fail "server did not stop within 180s"
        fi
        sleep 1
        waited=$((waited + 1))
    done
    SERVER_PID=""
    exec 3>&-
}

# ---------------------------------------------------------------- first boot
start_server first-boot.log
send "plugins"
send "lifesteal info"
send "lifesteal help"
send "lifesteal top"
send "lifesteal beacon list"
send "lifesteal check Notch"
send "lifesteal setheart Notch 5"
send "lifesteal give Notch small_heart"
send "ls debug"
send "ls debug"
send "lifesteal reload"
wait_for 'Reloaded by' 120 first-boot.log
sleep 5
stop_server first-boot.log

# ---------------------------------------------------------------- second boot (restart path)
start_server second-boot.log
send "lifesteal info"
sleep 5
stop_server second-boot.log

cat first-boot.log second-boot.log > all.log

echo "::group::Server log ($PLATFORM $VERSION)"
cat all.log
echo "::endgroup::"
echo "::group::Warnings and errors"
grep -nE '(WARN|ERROR|SEVERE)\]' all.log || echo "(none)"
echo "::endgroup::"

# ---------------------------------------------------------------- assertions
problems=0
must() { # <extended regex> <description>
    if ! grep -qE -- "$1" all.log; then
        echo "::error::missing: $2"
        problems=$((problems + 1))
    fi
}
must_not() { # <extended regex> <description>
    if grep -nE -- "$1" all.log; then
        echo "::error::found: $2"
        problems=$((problems + 1))
    fi
}

must 'LifeCore [0-9.]+ enabled in' "LifeCore enable message"
enables=$(grep -cE 'LifeCore [0-9.]+ enabled in' all.log || true)
[ "$enables" -eq 2 ] || { echo "::error::LifeCore enabled $enables time(s), expected 2"; problems=$((problems + 1)); }
must 'Created default config\.yml' "default configuration generated on first boot"
must 'Reloaded by' "successful /lifesteal reload"
must 'Storage: ' "/lifesteal info output"
must 'LifeCore disabled - all data saved' "clean shutdown"
must_not 'Error occurred while enabling LifeCore|Could not load .plugins/LifeCore|LifeCore failed to start' "plugin failed to load or enable"
must_not 'at com\.example\.lifecore' "stack trace from LifeCore code"
must_not 'NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|IncompatibleClassChangeError|AbstractMethodError' "API linkage error"
must_not 'configuration problem\(s\) found' "problems in the bundled default configuration"
must_not 'Missing message|Unknown sound|Unknown particle|Unhandled exception in|Could not hook into' "LifeCore warnings"
must_not 'Migrated (config|messages|items|beacons|effects|worlds|sounds|menus|storage)\.yml' "unexpected migration of freshly generated files"

if [ "$problems" -gt 0 ]; then
    echo "---- LifeCore log lines ----"
    grep -E 'LifeCore' all.log | tail -n 80 || true
    fail "$problems check(s) failed on $PLATFORM $VERSION"
fi
echo "LifeCore passed the smoke test on $PLATFORM $VERSION"

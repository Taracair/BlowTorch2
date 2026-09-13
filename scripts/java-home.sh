#!/usr/bin/env bash
# Pin JDK 17 for Gradle 8.2 / AGP 8.2.
#
# CachyOS's /usr/bin/java is 26. AGP 8.2's jlink dies on that
# (core-for-system-modules.jar → JdkImageTransform). F-Droid wants 17 too.
# Do not write the path into gradle.properties: Arch and Debian differ.
#
# Source from check.sh and deploy.sh (after cd to the repo root):
#   . scripts/java-home.sh || exit 1
# Running it prints JAVA_HOME and `java -version`.

bt_java_major() {
	local bin="$1"
	[ -x "$bin" ] || return 1
	"$bin" -version 2>&1 | awk -F '"' '/version/ {
		split($2, a, ".")
		if (a[1] == 1) print a[2]
		else print a[1]
		exit
	}'
}

bt_pick_java_home() {
	local c major
	for c in \
		"${JAVA_HOME:-}" \
		/usr/lib/jvm/java-17-openjdk \
		/usr/lib/jvm/java-17-openjdk-amd64 \
		/usr/lib/jvm/java-17-openjdk-arm64
	do
		[ -n "$c" ] || continue
		[ -x "$c/bin/java" ] || continue
		[ -x "$c/bin/javac" ] || continue
		[ -x "$c/bin/jlink" ] || continue
		major="$(bt_java_major "$c/bin/java")" || continue
		if [ "$major" = "17" ]; then
			printf '%s\n' "$c"
			return 0
		fi
	done
	return 1
}

bt_apply_java_home() {
	local picked
	picked="$(bt_pick_java_home)" || {
		echo "scripts/java-home.sh: need JDK 17." >&2
		echo "This machine's java is too new for Gradle 8.2 (AGP 8.2 jlink fails on 26)." >&2
		echo "Install a 17 package (Arch: jdk17-openjdk) and leave it next to the default." >&2
		return 1
	}
	export JAVA_HOME="$picked"
	export PATH="$JAVA_HOME/bin:$PATH"
	hash -r 2>/dev/null || true
	echo "java-home: $JAVA_HOME"
	return 0
}

bt_apply_java_home || return 1 2>/dev/null || exit 1

if [ "${BASH_SOURCE[0]-}" = "${0:-}" ]; then
	"$JAVA_HOME/bin/java" -version
fi

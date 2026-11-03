#!/bin/sh
# Usage: source scripts/env.sh (from the project root).
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null) || {
        printf '%s\n' 'JDK 17을 설치한 뒤 JAVA_HOME을 지정하세요.' >&2
        return 1
    }
    export JAVA_HOME
fi
if [ -z "${ANDROID_HOME:-}" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"


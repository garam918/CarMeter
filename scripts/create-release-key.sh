#!/bin/bash
# Creates the release signing key and the properties file the Gradle build reads, both
# outside the repository (~/.carmeter-signing by default). The password is typed here,
# never echoed, and never written inside the project.
#
# Back up the whole folder somewhere safe: an app signed with a lost key cannot be updated.
set -euo pipefail

DIR="${CARMETER_SIGNING_DIR:-$HOME/.carmeter-signing}"
STORE="$DIR/carmeter-release.jks"
PROPS="$DIR/keystore.properties"
ALIAS=carmeter

if [ -e "$STORE" ] || [ -e "$PROPS" ]; then
    echo "이미 키가 있습니다: $DIR (덮어쓰지 않습니다)" >&2
    exit 1
fi
if ! command -v keytool >/dev/null && JH=$(/usr/libexec/java_home -v 17 2>/dev/null); then
    PATH="$JH/bin:$PATH"
fi
if ! command -v keytool >/dev/null; then
    echo "keytool이 없습니다. JDK 17을 설치하거나 source scripts/env.sh 후 다시 실행하세요." >&2
    exit 1
fi

read -rsp "키 비밀번호 (8자 이상): " PW; echo
read -rsp "한 번 더 입력: " PW2; echo
if [ "$PW" != "$PW2" ]; then echo "두 비밀번호가 다릅니다." >&2; exit 1; fi
if [ ${#PW} -lt 8 ]; then echo "8자 이상이어야 합니다." >&2; exit 1; fi

umask 077
mkdir -p "$DIR"
chmod 700 "$DIR"

export CARMETER_STOREPASS="$PW"
# PKCS12 uses one password for the store and the key.
keytool -genkeypair -keystore "$STORE" -storetype PKCS12 -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=CarMeter" \
    -storepass:env CARMETER_STOREPASS
FINGERPRINT=$(keytool -list -v -keystore "$STORE" -storetype PKCS12 -alias "$ALIAS" \
    -storepass:env CARMETER_STOREPASS | grep "SHA256:" | sed 's/^[[:space:]]*//')
unset CARMETER_STOREPASS

# java.util.Properties treats a backslash as an escape.
ESCAPED=${PW//\\/\\\\}
printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' \
    "$STORE" "$ESCAPED" "$ALIAS" "$ESCAPED" > "$PROPS"
chmod 600 "$STORE" "$PROPS"
unset PW PW2 ESCAPED

echo
echo "만들었습니다: $STORE"
echo "Gradle 설정: $PROPS"
echo "인증서 지문: $FINGERPRINT"
echo "이 폴더를 안전한 곳에 백업하세요. 키를 잃으면 같은 앱으로 업데이트할 수 없습니다."

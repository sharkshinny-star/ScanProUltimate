#!/usr/bin/env bash
# Creates the Play upload key for ScanPro (or adopts one you already have) and tells Gradle where it is.
# Run it yourself, on your own computer, never in a cloud session:
#
#   bash scripts/setup-upload-key.sh                      # make a new key in ~/scanpro-keys/
#   bash scripts/setup-upload-key.sh /path/to/your.jks    # use a key that already exists
#
# Nothing is written inside the repository. The key file goes to ~/scanpro-keys/ and the four SCANPRO_* values
# that app/build.gradle.kts reads go to ~/.gradle/gradle.properties, readable by you only. The password is typed
# hidden, is never printed, and is handed to keytool through the environment, not the command line.
set -euo pipefail

KEY_DIR="$HOME/scanpro-keys"
KEYSTORE="${1:-$KEY_DIR/scanpro-upload.jks}"
GRADLE_PROPS="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

say() { printf '%s\n' "$*"; }
die() { printf 'Stopped: %s\n' "$*" >&2; exit 1; }

# keytool: JAVA_HOME first, then Android Studio's bundled JDK, then whatever is on the PATH.
KEYTOOL=""
for candidate in \
    "${JAVA_HOME:-/nonexistent}/bin/keytool" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" \
    "/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home/bin/keytool" \
    "$(command -v keytool 2>/dev/null || true)"; do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then KEYTOOL="$candidate"; break; fi
done
[ -n "$KEYTOOL" ] || die "keytool not found. Install Android Studio, or set JAVA_HOME to a JDK."

case "$KEYSTORE" in /*) ;; *) KEYSTORE="$PWD/$KEYSTORE" ;; esac
if git -C "$(dirname "$KEYSTORE")" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    die "$KEYSTORE is inside a git repository. Keep the key outside it, for example in $KEY_DIR."
fi

# Asks for a password without showing it. Backslashes and spaces are refused because a Gradle properties file
# would read them differently from how they were typed.
ask_password() { # $1 = prompt, result in $REPLY
    IFS= read -r -s -p "$1" REPLY; printf '\n'
    case "$REPLY" in *\\*|*" "*) die "Use a password without spaces or backslashes." ;; esac
}

if [ -f "$KEYSTORE" ]; then
    say "Using the existing key file: $KEYSTORE"
    ask_password "Its password: "; STORE_PW="$REPLY"
    export SCANPRO_PW_STORE="$STORE_PW"
    "$KEYTOOL" -list -keystore "$KEYSTORE" -storepass:env SCANPRO_PW_STORE >/dev/null 2>&1 \
        || die "That password does not open the key file."
    say "Keys in this file:"
    "$KEYTOOL" -list -keystore "$KEYSTORE" -storepass:env SCANPRO_PW_STORE 2>/dev/null | grep 'Entry,' | cut -d, -f1 | sed 's/^/  /'
    read -r -p "Key name (alias) to sign with [upload]: " ALIAS; ALIAS="${ALIAS:-upload}"
    "$KEYTOOL" -list -keystore "$KEYSTORE" -storepass:env SCANPRO_PW_STORE -alias "$ALIAS" >/dev/null 2>&1 \
        || die "No key named '$ALIAS' in that file."
    ask_password "Password of that key (press Return if it is the same): "; KEY_PW="${REPLY:-$STORE_PW}"
else
    say "This makes a NEW upload key at: $KEYSTORE"
    say "If ScanPro was ever uploaded to Play Console before, stop (Ctrl-C) and run this again with the path of"
    say "the key used then: Play only accepts updates signed with the same upload key."
    say ""
    read -r -p "Your name or company name (goes into the key's certificate): " OWNER
    [ -n "$OWNER" ] || die "A name is needed."
    ask_password "Choose a password (12 characters or more): "; STORE_PW="$REPLY"
    [ "${#STORE_PW}" -ge 12 ] || die "The password needs at least 12 characters."
    ask_password "Type it again: "
    [ "$REPLY" = "$STORE_PW" ] || die "The two passwords differ."
    ALIAS="upload"; KEY_PW="$STORE_PW"
    export SCANPRO_PW_STORE="$STORE_PW"
    mkdir -p "$(dirname "$KEYSTORE")"; chmod 700 "$(dirname "$KEYSTORE")"
    # 10,000 days is about 27 years; Play needs the key to outlive 22 October 2033.
    "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" \
        -keyalg RSA -keysize 4096 -validity 10000 \
        -dname "CN=$(printf '%s' "$OWNER" | sed 's/[,+="<>#;\\]/\\&/g')" \
        -storepass:env SCANPRO_PW_STORE -keypass:env SCANPRO_PW_STORE >/dev/null 2>&1 \
        || die "keytool could not create the key."
    chmod 600 "$KEYSTORE"
    say "Key created."
fi

# Replace any earlier SCANPRO_* signing lines in the user-level Gradle properties, keep everything else.
mkdir -p "$(dirname "$GRADLE_PROPS")"
touch "$GRADLE_PROPS"; chmod 600 "$GRADLE_PROPS"
TMP="$(mktemp "$GRADLE_PROPS.XXXXXX")"; chmod 600 "$TMP"
grep -v -E '^SCANPRO_(KEYSTORE|KEYSTORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)=' "$GRADLE_PROPS" > "$TMP" || true
{
    printf 'SCANPRO_KEYSTORE=%s\n' "$KEYSTORE"
    printf 'SCANPRO_KEYSTORE_PASSWORD=%s\n' "$STORE_PW"
    printf 'SCANPRO_KEY_ALIAS=%s\n' "$ALIAS"
    printf 'SCANPRO_KEY_PASSWORD=%s\n' "$KEY_PW"
} >> "$TMP"
mv "$TMP" "$GRADLE_PROPS"

say ""
say "Gradle now signs release builds with this key (settings saved in $GRADLE_PROPS)."
say "Certificate fingerprint, for your records:"
"$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass:env SCANPRO_PW_STORE -alias "$ALIAS" 2>/dev/null \
    | grep -E 'SHA-?256:' | sed 's/^[[:space:]]*/  /'
say ""
say "Do these two things now:"
say "  1. Save the password in a password manager."
say "  2. Copy $KEYSTORE to a second safe place (not this repository, not a public folder)."
say "Never send the key file or the password to anyone, and never paste them into a chat."

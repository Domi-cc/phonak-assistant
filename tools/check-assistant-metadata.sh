#!/bin/sh
# Fails when the metadata that makes the app Android's digital assistant is incomplete.
#
# Why: Android re-checks the assistant role on every package change. A build whose
# voice-interaction XML lacks sessionService, recognitionService or supportsAssist="true", or
# whose manifest lacks one of the three services, is dropped from the role without any message
# and the phone's default assistant takes over. The hearing-aid tap has the same weakness:
# Android sends it to the activity the owner once chose as "Always" for VOICE_COMMAND and forgets
# that choice when the activity is renamed, disabled or loses its filter. The compiler does not
# look at any of this and lint does not stop the build, so CI and the release workflow run this
# script before they build.
#
# It reads source files only: no SDK, no build. POSIX sh has no XML parser, so it matches text.
# Comments are removed and blanks normalised first; then every check looks for name="value"
# inside the element it belongs to.
#
# Usage: sh tools/check-assistant-metadata.sh    (from any directory, no arguments)

set -u

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd) || exit 2
manifest=$root/app/src/main/AndroidManifest.xml
service_xml=$root/app/src/main/res/xml/voice_interaction_service.xml
build_file=$root/app/build.gradle.kts

tab=$(printf '\t')
failures=0

# Reports one problem and carries on, so a single run lists everything that is wrong.
fail() {
    failures=$((failures + 1))
    if [ "${GITHUB_ACTIONS:-}" = true ]; then
        printf '::error title=Assistant metadata incomplete::%s\n' "$1"
    else
        printf 'check-assistant-metadata: %s\n' "$1" >&2
    fi
}

# Prints an XML file as one tag per line, without the leading "<": comments removed, line breaks
# and repeated blanks collapsed, no blanks around "=", single quotes turned into double quotes.
tags() {
    awk '
        {
            rest = $0
            while (rest != "") {
                if (comment) {
                    at = index(rest, "-->")
                    if (at == 0) break
                    rest = substr(rest, at + 3)
                    comment = 0
                } else {
                    at = index(rest, "<!--")
                    if (at == 0) { printf "%s", rest; break }
                    printf "%s", substr(rest, 1, at - 1)
                    rest = substr(rest, at + 4)
                    comment = 1
                }
            }
            printf " "
        }
        END { printf "\n" }
    ' "$1" | tr "'<" '"\n' | sed -e 's/[[:space:]][[:space:]]*/ /g' -e 's/ *= */=/g'
}

# Prints the value of attribute $2 in the tag text $1; nothing if it is absent or empty.
attribute() {
    printf '%s\n' "$1" | sed -n "s/.* $2=\"\([^\"]*\)\".*/\1/p"
}

# Succeeds if text $1 contains the literal string $2.
contains() {
    case $1 in
        *"$2"*) return 0 ;;
    esac
    return 1
}

for file in "$manifest" "$service_xml" "$build_file"; do
    [ -f "$file" ] || fail "file not found: ${file#"$root"/}"
done
[ "$failures" -eq 0 ] || exit 1

# --- res/xml/voice_interaction_service.xml: the three attributes the role needs ----------------

service_tag=$(tags "$service_xml" | grep '^voice-interaction-service[ />]')
session_class=$(attribute "$service_tag" android:sessionService)
recognition_class=$(attribute "$service_tag" android:recognitionService)

[ -n "$session_class" ] ||
    fail 'voice_interaction_service.xml: android:sessionService is missing'
[ -n "$recognition_class" ] ||
    fail 'voice_interaction_service.xml: android:recognitionService is missing'
contains "$service_tag" ' android:supportsAssist="true"' ||
    fail 'voice_interaction_service.xml: android:supportsAssist="true" is missing'

# --- AndroidManifest.xml: the three services and the voice-button activity ---------------------

# The manifest may shorten class names to ".assistant.Foo"; the XML above must not. The
# namespace makes both comparable.
namespace=$(sed -n 's/^[[:space:]]*namespace[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$build_file" | head -n 1)
if [ -z "$namespace" ]; then
    fail 'app/build.gradle.kts: namespace not found, class names cannot be resolved'
    exit 1
fi

# One line per <service> and <activity> of the manifest:
#   kind <TAB> full class name <TAB> the element with everything inside it
# Keeping the children on the line ties a filter or meta-data entry to its own component.
components=$(tags "$manifest" | awk -v ns="$namespace" '
    function finish(    head, name, at) {
        head = substr(block, 1, index(block, ">"))
        name = ""
        at = index(head, " android:name=\"")
        if (at > 0) {
            name = substr(head, at + 15)
            name = substr(name, 1, index(name, "\"") - 1)
        }
        if (substr(name, 1, 1) == ".") name = ns name
        else if (name != "" && index(name, ".") == 0) name = ns "." name
        printf "%s\t%s\t%s\n", kind, name, block
        inside = 0
    }
    $1 == "service" || $1 == "activity" {
        kind = $1
        block = $0
        inside = 1
        tag = $0
        sub(/ *$/, "", tag)
        if (substr(tag, length(tag) - 1) == "/>") finish()
        next
    }
    inside && ($1 == "/" kind ">" || $1 == "/" kind) { finish(); next }
    inside { block = block "<" $0 }
')

# Prints the element text of the component of kind $1 with the full class name $2.
component() {
    printf '%s\n' "$components" |
        awk -F '\t' -v kind="$1" -v name="$2" '$1 == kind && $2 == name { print $3 }'
}

# The class names are plain strings to the build. Nothing else notices when a class is renamed
# or moved while the XML or the manifest still names the old one.
require_source() {
    path=$(printf '%s' "$1" | tr . /)
    for candidate in kotlin/"$path".kt java/"$path".kt java/"$path".java; do
        [ -f "$root/app/src/main/$candidate" ] && return 0
    done
    fail "no source file for $1 (expected app/src/main/kotlin/$path.kt)"
}

require_enabled() {
    if contains "$2" ' android:enabled="false"'; then
        fail "AndroidManifest.xml: $1 is disabled"
    fi
}

# 1. The service Android binds while the app holds the role. The role check finds it by its
#    intent action and insists on the bind permission and on the meta-data checked above.
interaction=$(printf '%s\n' "$components" | grep "^service$tab" |
    grep -F '<action android:name="android.service.voice.VoiceInteractionService"' | head -n 1)
if [ -z "$interaction" ]; then
    fail 'AndroidManifest.xml: no <service> with the intent action android.service.voice.VoiceInteractionService'
else
    interaction_class=$(printf '%s\n' "$interaction" | cut -f 2)
    contains "$interaction" ' android:permission="android.permission.BIND_VOICE_INTERACTION"' ||
        fail "AndroidManifest.xml: $interaction_class must require android.permission.BIND_VOICE_INTERACTION"
    if ! contains "$interaction" ' android:name="android.voice_interaction"' ||
        ! contains "$interaction" ' android:resource="@xml/voice_interaction_service"'; then
        fail "AndroidManifest.xml: $interaction_class needs <meta-data android:name=\"android.voice_interaction\" android:resource=\"@xml/voice_interaction_service\" />"
    fi
    require_enabled "$interaction_class" "$interaction"
    require_source "$interaction_class"
fi

# 2. The session service named by the XML.
if [ -n "$session_class" ]; then
    session=$(component service "$session_class")
    if [ -z "$session" ]; then
        fail "AndroidManifest.xml: no <service> named $session_class (sessionService in voice_interaction_service.xml)"
    else
        require_enabled "$session_class" "$session"
        require_source "$session_class"
    fi
fi

# 3. The recognition service named by the XML. A stub, but without it Android clears the
#    assistant setting again.
if [ -n "$recognition_class" ]; then
    recognition=$(component service "$recognition_class")
    if [ -z "$recognition" ]; then
        fail "AndroidManifest.xml: no <service> named $recognition_class (recognitionService in voice_interaction_service.xml)"
    else
        contains "$recognition" '<action android:name="android.speech.RecognitionService"' ||
            fail "AndroidManifest.xml: $recognition_class lacks the intent action android.speech.RecognitionService"
        require_enabled "$recognition_class" "$recognition"
        require_source "$recognition_class"
    fi
fi

# 4. The activity the hearing-aid tap starts. Its name is fixed on purpose: the phone remembers
#    the owner's "Always" choice by component name.
voice_command_class=$namespace.trigger.VoiceCommandActivity
voice_command=$(component activity "$voice_command_class")
if [ -z "$voice_command" ]; then
    fail "AndroidManifest.xml: no <activity> named $voice_command_class (never rename or move it)"
else
    contains "$voice_command" '<action android:name="android.intent.action.VOICE_COMMAND"' ||
        fail "AndroidManifest.xml: $voice_command_class lacks the intent action android.intent.action.VOICE_COMMAND"
    contains "$voice_command" '<category android:name="android.intent.category.DEFAULT"' ||
        fail "AndroidManifest.xml: $voice_command_class lacks the category android.intent.category.DEFAULT"
    contains "$voice_command" ' android:exported="true"' ||
        fail "AndroidManifest.xml: $voice_command_class must be exported, the Bluetooth stack starts it"
    require_enabled "$voice_command_class" "$voice_command"
    require_source "$voice_command_class"
fi

if [ "$failures" -gt 0 ]; then
    printf 'check-assistant-metadata: %s problem(s). A build like this silently loses the assistant role or the hearing-aid tap on the phone.\n' "$failures" >&2
    exit 1
fi
printf 'check-assistant-metadata: ok (%s, %s, %s, %s)\n' \
    "${interaction_class##*.}" "${session_class##*.}" "${recognition_class##*.}" "${voice_command_class##*.}"

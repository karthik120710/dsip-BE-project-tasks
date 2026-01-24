#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper startup script
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != true ] || set -x

# OS specific support
case "$(uname)" in
CYGWIN* | MINGW*)
  export JAVA_HOME="${JAVA_HOME-}" && JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  ;;
esac

# Resolve links and absolute path
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' >/dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done
BASEDIR=$(cd "$(dirname "$PRG")" && pwd)

# Find the wrapper jar
MAVEN_WRAPPER_JAR="${BASEDIR}/.mvn/wrapper/maven-wrapper.jar"

# Download the wrapper if not present
if [ ! -r "$MAVEN_WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper..."
  WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "Neither curl nor wget found. Please install one of them."
    exit 1
  fi
fi

# Find Java
if [ -n "${JAVA_HOME-}" ]; then
  JAVA_CMD="${JAVA_HOME}/bin/java"
  if [ ! -x "$JAVA_CMD" ]; then
    echo "JAVA_HOME is set but java was not found at $JAVA_CMD"
    exit 1
  fi
else
  JAVA_CMD="java"
  if ! command -v java >/dev/null 2>&1; then
    echo "Java not found. Please install Java 21 or set JAVA_HOME."
    exit 1
  fi
fi

# Run Maven
exec "$JAVA_CMD" \
  ${MAVEN_OPTS-} \
  -jar "$MAVEN_WRAPPER_JAR" \
  "$@"

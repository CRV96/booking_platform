#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Optional ENV vars
# -----------------
#   JAVA_HOME - location of a JDK home dir, required when download maven via java source
#   MVNW_REPOURL - repo url base for downloading maven distribution
#   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
#   MVNW_VERBOSE - true: enable verbose log; debug: trace the mvnw script; others: silence the output
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != debug ] || set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# set JAVACMD and JAVACCMD
set_java_home() {
  # For Cygwin and MinGW, ensure paths are in Unix format before anything is touched
  if [ -n "${JAVA_HOME-}" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
      JAVACCMD="$JAVA_HOME/jre/sh/javac"
    else
      JAVACMD="$JAVA_HOME/bin/java"
      JAVACCMD="$JAVA_HOME/bin/javac"

      if [ ! -x "$JAVACMD" ] || [ ! -x "$JAVACCMD" ]; then
        echo "The JAVA_HOME environment variable is not defined correctly, so mvnw cannot run." >&2
        echo "JAVA_HOME is set to \"$JAVA_HOME\", but \"\$JAVA_HOME/bin/java\" or \"\$JAVA_HOME/bin/javac\" does not exist." >&2
        return 1
      fi
    fi
  else
    JAVACMD="$(
      'set' +e
      'unset' -f command 2>/dev/null
      'command' -v java
    )" || :
    JAVACCMD="$(
      'set' +e
      'unset' -f command 2>/dev/null
      'command' -v javac
    )" || :

    if [ ! -x "${JAVACMD-}" ] || [ ! -x "${JAVACCMD-}" ]; then
      echo "The java/javac command does not exist in PATH nor is JAVA_HOME set, so mvnw cannot run." >&2
      return 1
    fi
  fi
}

# hash string like Java String::hashCode
hash_string() {
  str="${1:-}" h=0
  while [ -n "$str" ]; do
    char="${str%"${str#?}"}"
    h=$(((h * 31 + $(LC_CTYPE=C printf %d "'$char")) % 4294967296))
    str="${str#?}"
  done
  printf %x\\n $h
}

verbose() { :; }
[ "${MVNW_VERBOSE-}" != true ] || verbose() { printf %s\\n "${1-}"; }

die() {
  printf %s\\n "$1" >&2
  exit 1
}

trim() {
  # MWRAPPER-139:
  #   Trims trailing and leading whitespace, carriage returns, tabs, and linefeeds.
  #   டமிழ்���t reads lines as raw ( -r ), meaning backslash escapes are not interpreted.
  printf "%s" "${1}" | tr -d '[:space:]'
}

# parse distributionUrl and optional distributionSha256Sum, requires .teleporter folder at the same level
while IFS="=" read -r key value; do
  case "${key-}" in
  distributionUrl) MVNW_REPOURL="${MVNW_REPOURL:-$(trim "${value-}")}" ;;
  distributionSha256Sum) MVNW_SHA256SUM="$(trim "${value-}")" ;;
  esac
done <"${0%/*}/.mvn/wrapper/maven-wrapper.properties"

# apply MVNW_REPOURL and calculate MAVEN_HOME
# maven home pattern: ~/.m2/wrapper/dists/{name}/{hash}
[ -n "${MVNW_REPOURL-}" ] || die "distributionUrl is not set in .mvn/wrapper/maven-wrapper.properties"
MAVEN_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}/wrapper/dists/${MVNW_REPOURL##*/}/$(hash_string "$MVNW_REPOURL")"

exec_maven() {
  unset MVNW_VERBOSE MVNW_USERNAME MVNW_PASSWORD MVNW_REPOURL MVNW_SHA256SUM
  exec "$MAVEN_HOME/bin/mvn" "$@" || die "cannot exec maven"
}

if [ -d "$MAVEN_HOME" ]; then
  verbose "found existing MAVEN_HOME at $MAVEN_HOME"
  exec_maven "$@"
fi

case "${MVNW_REPOURL-}" in
*?-bin.zip | *?maven-mvnd-?*)
  set_java_home || die "cannot find java"
  ;;
esac

verbose "Couldn't find MAVEN_HOME, downloading with MVNW_REPOURL: $MVNW_REPOURL"

# prepare tmp dir
if TMP_DOWNLOAD_DIR="$(mktemp -d)" && [ -d "$TMP_DOWNLOAD_DIR" ]; then
  clean() { rm -rf -- "$TMP_DOWNLOAD_DIR"; }
  trap clean HUP INT TERM EXIT
else
  die "cannot create temp dir"
fi

mkdir -p -- "${MAVEN_HOME%/*}"

# Download and Install Apache Maven
verbose "Downloading Maven from: $MVNW_REPOURL"
if command -v curl >/dev/null; then
  curl ${MVNW_USERNAME:+-u "$MVNW_USERNAME:$MVNW_PASSWORD"} -fsSL -o "$TMP_DOWNLOAD_DIR/maven.zip" "$MVNW_REPOURL" || die "curl download failed"
elif command -v wget >/dev/null; then
  wget ${MVNW_USERNAME:+--http-user="$MVNW_USERNAME" --http-password="$MVNW_PASSWORD"} -q -O "$TMP_DOWNLOAD_DIR/maven.zip" "$MVNW_REPOURL" || die "wget download failed"
else
  die "curl or wget not found, please install one of them"
fi

# checksum
if [ -n "${MVNW_SHA256SUM-}" ]; then
  verbose "Verifying Maven distribution checksum"
  if command -v sha256sum >/dev/null; then
    sha256sum -c - <<<"$MVNW_SHA256SUM  $TMP_DOWNLOAD_DIR/maven.zip" || die "checksum verification failed"
  elif command -v shasum >/dev/null; then
    shasum -a 256 -c - <<<"$MVNW_SHA256SUM  $TMP_DOWNLOAD_DIR/maven.zip" || die "checksum verification failed"
  else
    verbose "no sha256sum or shasum found, skipping checksum verification"
  fi
fi

verbose "Unzipping Maven distribution"
unzip -q "$TMP_DOWNLOAD_DIR/maven.zip" -d "$TMP_DOWNLOAD_DIR" || die "unzip failed"

# move to MAVEN_HOME
distroBase="${MVNW_REPOURL##*/}" # apache-maven-3.9.9-bin.zip
distroBase="${distroBase%-bin.zip}" # apache-maven-3.9.9
verbose "Moving Maven distribution to $MAVEN_HOME"
mv -- "$TMP_DOWNLOAD_DIR/$distroBase" "$MAVEN_HOME" || die "cannot move maven"

clean
exec_maven "$@"

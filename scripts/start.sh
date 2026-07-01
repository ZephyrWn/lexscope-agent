#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "[lexscope-agent] building..."
mvn -B -ntp -DskipTests clean package

echo "[lexscope-agent] starting..."
java -jar target/lexscope-agent-1.0-SNAPSHOT.jar

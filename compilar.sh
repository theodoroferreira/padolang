#!/usr/bin/env bash
# Compila o transpilador para a pasta out/
set -e
cd "$(dirname "$0")"
rm -rf out
javac -encoding UTF-8 -d out $(find src -name "*.java")
echo "Transpilador compilado em out/"

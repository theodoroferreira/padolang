#!/usr/bin/env bash
# Compila o transpilador para a pasta out/ e empacota em out/padolang.jar
set -e
cd "$(dirname "$0")"
rm -rf out
javac -encoding UTF-8 -d out $(find src -name "*.java")
jar --create --file out/padolang.jar --main-class padolang.Main -C out padolang
echo "Transpilador compilado em out/ (e empacotado em out/padolang.jar)"

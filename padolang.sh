#!/usr/bin/env bash
# Transpila, compila e executa um programa Padolang.
#   ./padolang.sh testes/02_valido_completo.padoin [--tokens] [--ast]
# O .c e o executável ficam na pasta saida/.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
[ -d "$DIR/out" ] || "$DIR/compilar.sh" > /dev/null

FONTE="$1"; shift || true
NOME="$(basename "${FONTE%.padoin}")"
C="$DIR/saida/$NOME.c"
EXE="$DIR/saida/$NOME"

java -cp "$DIR/out" padolang.Main "$FONTE" -o "$C" "$@"
gcc -std=c11 -Wall -Wextra -o "$EXE" "$C"
echo "---------- executando $NOME ----------" >&2
"$EXE"

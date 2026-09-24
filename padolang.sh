#!/usr/bin/env bash
# Transpila, compila e executa um programa padolang.
#   ./padolang.sh testes/02_valido_completo.padoin [--tokens] [--ast]
# O .c e o executável ficam na pasta saida/.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 1 ]; then
  echo "Uso: ./padolang.sh arquivo.padoin [--tokens] [--ast]" >&2
  exit 2
fi

# Recompila o transpilador se ele ainda não existe ou se algum .java mudou depois da última compilação
MAIN="$DIR/out/padolang/Main.class"
if [ ! -f "$MAIN" ] || [ -n "$(find "$DIR/src" -name '*.java' -newer "$MAIN" | head -n 1)" ]; then
  "$DIR/compilar.sh" > /dev/null
fi

FONTE="$1"; shift
NOME="$(basename "${FONTE%.padoin}")"
C="$DIR/saida/$NOME.c"
EXE="$DIR/saida/$NOME"

java -cp "$DIR/out" padolang.Main "$FONTE" -o "$C" "$@"

# -fwrapv: estouro de inteiro dá a volta, como no Java.
# Avisos de variável/parâmetro não usado são desligados: não são erros do programa padolang.
gcc -std=c11 -fwrapv -Wall -Wextra \
    -Wno-unused-variable -Wno-unused-parameter -Wno-unused-but-set-variable \
    -o "$EXE" "$C"

echo "---------- executando $NOME ----------" >&2
# exec: o programa substitui este script, então quem chamou (e o timeout do testar.sh)
# fala direto com ele, e o código de saída é o do programa.
exec "$EXE"

#!/usr/bin/env bash
# Transpila, compila e executa um programa padolang.
#   ./padolang.sh testes/02_valido_completo.padoin [--tokens] [--ast]
# O .c e o executável ficam na pasta saida/.
# É só um atalho para "bin/padolang run": o bin/padolang recompila o transpilador se algum
# .java mudou, e o runner (src/padolang/runner) chama o gcc e executa o programa.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 1 ]; then
  echo "Uso: ./padolang.sh arquivo.padoin [--tokens] [--ast]" >&2
  exit 2
fi

# exec: quem chamou (e o timeout do testar.sh) fala direto com o runner,
# e o código de saída é o do programa.
exec "$DIR/bin/padolang" run --dir "$DIR/saida" "$@"

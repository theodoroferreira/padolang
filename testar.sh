#!/usr/bin/env bash
# Roda todos os programas da pasta testes/. A entrada padrão recebe "3"
# para os programas que usam padoIN.
cd "$(dirname "$0")"
./compilar.sh > /dev/null
for f in testes/*.padoin; do
  echo
  echo "=================== $f"
  echo 3 | ./padolang.sh "$f"
  echo "(código de saída: $?)"
done

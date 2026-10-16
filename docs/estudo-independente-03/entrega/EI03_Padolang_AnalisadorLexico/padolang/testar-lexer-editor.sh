#!/usr/bin/env bash
# Confere o léxico no modo editor (usado pelo formatter e pelo LSP).
#
# Para cada testes/lexer_editor/<nome>.padoin, a saída de "--tokens --editor" (tokens com
# tamanho, comentários, trechos inválidos e erros léxicos) precisa ser igual a <nome>.tokens.
# Sai com código 1 se algum caso falhar.
cd "$(dirname "$0")"
./compilar.sh > /dev/null || exit 1

total=0
falhas=0

for f in testes/lexer_editor/*.padoin; do
  total=$((total + 1))
  esperado="${f%.padoin}.tokens"
  obtido="$(java -cp out padolang.Main "$f" --tokens --editor 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS')"
  if diff -q "$esperado" <(echo "$obtido") > /dev/null; then
    echo "[ OK ]  $f"
  else
    falhas=$((falhas + 1))
    echo "[FALHA] $f — a saída é diferente de $esperado"
    diff "$esperado" <(echo "$obtido") | sed 's/^</        esperado:/; s/^>/        obtido:  /'
  fi
done

echo
echo "$((total - falhas)) de $total casos passaram."
[ $falhas -eq 0 ]

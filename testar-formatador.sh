#!/usr/bin/env bash
# Confere o formatador (padolang fmt):
#   - cada testes/formatador/<nome>.padoin formatado é igual a <nome>.formatado;
#   - idempotência: formatar o <nome>.formatado não muda nada;
#   - todos os testes/*.padoin e exemplos/*.padoin válidos já estão formatados (fmt --check);
#   - o formatador não muda o comportamento: uma cópia do projeto com tudo formatado
#     passa no testar.sh;
#   - erros: arquivo com erro léxico ou sintático não é alterado, --check acusa o que mudaria,
#     e o fim de linha CRLF é mantido.
# Sai com código 1 se algum caso falhar.
cd "$(dirname "$0")"
./compilar.sh > /dev/null || exit 1

P=bin/padolang
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

total=0
falhas=0

ok() { total=$((total + 1)); echo "[ OK ]  $1"; }
falha() { total=$((total + 1)); falhas=$((falhas + 1)); echo "[FALHA] $1"; }

# fmt --stdout, sem a linha do JAVA_TOOL_OPTIONS
formatar() { $P fmt --stdout "$1" 2> /dev/null; }

# pares .padoin / .formatado e idempotência
for f in testes/formatador/*.padoin; do
  esperado="${f%.padoin}.formatado"
  if diff -q "$esperado" <(formatar "$f") > /dev/null; then
    ok "$f"
  else
    falha "$f — o resultado é diferente de $esperado"
    diff "$esperado" <(formatar "$f") | sed 's/^</        esperado:/; s/^>/        obtido:  /'
  fi
  if diff -q "$esperado" <(formatar "$esperado") > /dev/null; then
    ok "idempotente: $esperado"
  else
    falha "idempotente: $esperado — formatar de novo muda o arquivo"
    diff "$esperado" <(formatar "$esperado") | sed 's/^</        antes:  /; s/^>/        depois: /'
  fi
done

# os programas do repositório já estão formatados (os com erro léxico ou sintático ficam de fora)
validos=()
for f in testes/*.padoin exemplos/*.padoin; do
  $P ast "$f" > /dev/null 2>&1 && validos+=("$f")   # passa no léxico e no sintático
done
if msg="$($P fmt --check "${validos[@]}" 2>&1)"; then
  ok "fmt --check nos ${#validos[@]} programas válidos de testes/ e exemplos/"
else
  falha "fmt --check nos programas de testes/ e exemplos/"
  grep -v '^Picked up JAVA_TOOL_OPTIONS' <<< "$msg" | sed 's/^/        /'
fi

# o formatador não muda o comportamento: formata uma cópia de tudo e roda o testar.sh nela
mkdir -p "$TMP/projeto"
cp -r src bin testes exemplos compilar.sh padolang.sh testar.sh "$TMP/projeto/"
(cd "$TMP/projeto" && ./compilar.sh > /dev/null && bin/padolang fmt "${validos[@]}" > /dev/null 2>&1)
# a cópia também recebe os casos "bagunçados" do formatador, formatados, como programas a executar
for f in testes/formatador/*.padoin; do
  cp "$f" "$TMP/projeto/testes/fmt_$(basename "$f")"
  (cd "$TMP/projeto" && bin/padolang fmt "testes/fmt_$(basename "$f")" > /dev/null 2>&1)
done
if msg="$("$TMP/projeto/testar.sh" 2>&1)"; then
  ok "testar.sh numa cópia formatada ($(tail -n 1 <<< "$msg"))"
else
  falha "testar.sh numa cópia formatada"
  grep -v '^Picked up JAVA_TOOL_OPTIONS' <<< "$msg" | grep -v '^\[ OK \]' | sed 's/^/        /'
fi

# arquivo com erro sintático: sai com 1, mostra o padoERRO e não altera o arquivo
cp testes/04_erro_sintatico.padoin "$TMP/erro.padoin"
msg="$($P fmt "$TMP/erro.padoin" 2>&1)"; codigo=$?
if [ $codigo -eq 1 ] && grep -q "padoERRO sintático" <<< "$msg" && cmp -s testes/04_erro_sintatico.padoin "$TMP/erro.padoin"; then
  ok "erro sintático: sai com 1 e não altera o arquivo"
else
  falha "erro sintático: saiu com $codigo — $msg"
fi

# --check acusa o arquivo que mudaria, sem alterá-lo; depois do fmt, passa
cp testes/formatador/baguncado.padoin "$TMP/baguncado.padoin"
msg="$($P fmt --check "$TMP/baguncado.padoin" 2>&1)"; codigo=$?
if [ $codigo -eq 1 ] && grep -q "precisa ser formatado" <<< "$msg" && cmp -s testes/formatador/baguncado.padoin "$TMP/baguncado.padoin"; then
  ok "--check acusa arquivo não formatado"
else
  falha "--check acusa arquivo não formatado — saiu com $codigo"
fi
$P fmt "$TMP/baguncado.padoin" > /dev/null 2>&1
if $P fmt --check "$TMP/baguncado.padoin" > /dev/null 2>&1 && cmp -s testes/formatador/baguncado.formatado "$TMP/baguncado.padoin"; then
  ok "fmt grava no arquivo"
else
  falha "fmt grava no arquivo"
fi

# CRLF: o fim de linha do original é mantido
printf 'PADOIN {\r\npadoPRINTLN(1)👍\r\n}\r\n' > "$TMP/crlf.padoin"
if [ "$(formatar "$TMP/crlf.padoin" | od -An -c | tr -s ' ')" = "$(printf 'PADOIN {\r\n  padoPRINTLN(1)👍\r\n}\r\n' | od -An -c | tr -s ' ')" ]; then
  ok "CRLF mantido"
else
  falha "CRLF mantido"
fi

# opções inválidas
if $P fmt --check --stdout exemplos/primos.padoin > /dev/null 2>&1; [ $? -eq 2 ]; then
  ok "--check com --stdout é erro de uso"
else
  falha "--check com --stdout é erro de uso"
fi

echo
echo "$((total - falhas)) de $total casos passaram."
[ $falhas -eq 0 ]

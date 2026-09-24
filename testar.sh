#!/usr/bin/env bash
# Roda todos os programas de testes/ e exemplos/.
#
#   ./testar.sh        mostra só o resultado de cada caso
#   ./testar.sh -v     mostra também a saída dos programas e as mensagens de erro
#
# Regras:
#   - arquivos com "erro" no nome precisam ser REJEITADOS pelo transpilador, com uma
#     mensagem que começa com "Erro". Se o arquivo tiver uma linha
#     "# espera-erro: <trecho>", a mensagem também precisa conter esse trecho
#     (assim o teste confere que o erro certo foi apontado, não um erro qualquer);
#   - os demais precisam transpilar, compilar com gcc e executar sem falha;
#   - se existir <nome>.entrada ao lado do .padoin, ele vira a entrada padrão do programa.
# Sai com código 1 se algum caso falhar.
cd "$(dirname "$0")"
./compilar.sh > /dev/null || exit 1
mkdir -p saida

VERBOSO=0
[ "$1" = "-v" ] && VERBOSO=1

total=0
falhas=0

for f in testes/*.padoin exemplos/*.padoin; do
  total=$((total + 1))
  nome="$(basename "${f%.padoin}")"
  entrada="${f%.padoin}.entrada"
  [ -f "$entrada" ] || entrada=/dev/null

  if [[ "$nome" == *erro* ]]; then
    msg="$(java -cp out padolang.Main "$f" -o "saida/$nome.c" 2>&1 >/dev/null)"
    codigo=$?
    esperado="$(sed -n 's/^#[[:space:]]*espera-erro:[[:space:]]*//p' "$f" | head -n 1)"
    if [ $codigo -eq 0 ]; then
      motivo="deveria ter sido rejeitado, mas foi aceito"
    elif ! grep -q '^Erro' <<< "$msg"; then
      motivo="a mensagem não começa com \"Erro\""
    elif [ -n "$esperado" ] && ! grep -qF -- "$esperado" <<< "$msg"; then
      motivo="esperava a mensagem conter: $esperado"
    else
      motivo=""
    fi
  else
    msg="$(./padolang.sh "$f" < "$entrada" 2>&1)"
    codigo=$?
    [ $codigo -eq 0 ] && motivo="" || motivo="falhou com código $codigo"
  fi

  if [ -z "$motivo" ]; then
    echo "[ OK ]  $f"
  else
    falhas=$((falhas + 1))
    echo "[FALHA] $f — $motivo"
  fi
  if [ $VERBOSO -eq 1 ] || [ -n "$motivo" ]; then
    grep -v '^Picked up JAVA_TOOL_OPTIONS' <<< "$msg" | sed 's/^/        /'
  fi
done

echo
echo "$((total - falhas)) de $total casos passaram."
[ $falhas -eq 0 ]

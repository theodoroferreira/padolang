#!/usr/bin/env bash
# Roda todos os programas de testes/ e exemplos/.
#
#   ./testar.sh        mostra só o resultado de cada caso
#   ./testar.sh -v     mostra também a saída dos programas e as mensagens de erro
#
# Regras, pelo nome do arquivo:
#   - "erro_execucao" no nome: o programa precisa transpilar e compilar, mas terminar com um
#     erro de execução ("padoERRO de execução ...");
#   - outro "erro" no nome: o transpilador precisa REJEITAR o programa, com mensagem que
#     começa com "padoERRO";
#   - nos dois casos de erro, uma linha "# espera-erro: <trecho>" no arquivo exige que a
#     mensagem contenha esse trecho (assim o teste confere que o erro certo foi apontado);
#   - os demais precisam transpilar, compilar e executar sem falha. Se existir <nome>.saida,
#     a saída do programa precisa ser exatamente igual a ela.
#   - se existir <nome>.entrada, ele vira a entrada padrão do programa.
# Cada programa tem até LIMITE segundos para terminar (um laço infinito não trava a suíte).
# Sai com código 1 se algum caso falhar.
cd "$(dirname "$0")"
./compilar.sh > /dev/null || exit 1
mkdir -p saida

VERBOSO=0
[ "$1" = "-v" ] && VERBOSO=1
LIMITE=10

total=0
falhas=0

for f in testes/*.padoin exemplos/*.padoin; do
  total=$((total + 1))
  nome="$(basename "${f%.padoin}")"
  base="${f%.padoin}"
  entrada="$base.entrada"
  [ -f "$entrada" ] || entrada=/dev/null
  esperado="$(sed -n 's/^#[[:space:]]*espera-erro:[[:space:]]*//p' "$f" | head -n 1)"
  motivo=""

  if [[ "$nome" == *erro_execucao* ]]; then
    msg="$(timeout "$LIMITE" ./padolang.sh "$f" < "$entrada" 2>&1)"
    codigo=$?
    if [ $codigo -eq 0 ]; then
      motivo="deveria terminar com erro de execução, mas terminou normalmente"
    elif [ $codigo -eq 124 ]; then
      motivo="passou de ${LIMITE}s (laço infinito?)"
    elif ! grep -q '^padoERRO de execução' <<< "$msg"; then
      motivo="esperava uma mensagem \"padoERRO de execução\""
    elif [ -n "$esperado" ] && ! grep -qF -- "$esperado" <<< "$msg"; then
      motivo="esperava a mensagem conter: $esperado"
    fi

  elif [[ "$nome" == *erro* ]]; then
    msg="$(java -cp out padolang.Main "$f" -o "saida/$nome.c" 2>&1 >/dev/null)"
    codigo=$?
    if [ $codigo -eq 0 ]; then
      motivo="deveria ter sido rejeitado, mas foi aceito"
    elif ! grep -q '^padoERRO' <<< "$msg"; then
      motivo="a mensagem não começa com \"padoERRO\""
    elif [ -n "$esperado" ] && ! grep -qF -- "$esperado" <<< "$msg"; then
      motivo="esperava a mensagem conter: $esperado"
    fi

  else
    timeout "$LIMITE" ./padolang.sh "$f" < "$entrada" > "saida/$nome.out" 2> "saida/$nome.err"
    codigo=$?
    msg="$(cat "saida/$nome.err" "saida/$nome.out")"
    if [ $codigo -eq 124 ]; then
      motivo="passou de ${LIMITE}s (laço infinito?)"
    elif [ $codigo -ne 0 ]; then
      motivo="falhou com código $codigo"
    elif [ -f "$base.saida" ] && ! diff -q "$base.saida" "saida/$nome.out" > /dev/null; then
      motivo="a saída é diferente de $base.saida"
      msg="$(diff "$base.saida" "saida/$nome.out" | sed 's/^</esperado:/; s/^>/obtido:  /')"
    fi
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

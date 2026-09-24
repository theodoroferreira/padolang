#!/usr/bin/env bash
# Confere a CLI (bin/padolang): subcomandos, saída --json e códigos de saída
# (0 ok, 1 erro no programa, 2 erro de uso ou de arquivo).
# Sai com código 1 se algum caso falhar.
cd "$(dirname "$0")"
./compilar.sh > /dev/null || exit 1
mkdir -p saida

P=bin/padolang
total=0
falhas=0

# confere <descrição> <código esperado> <trecho esperado na saída (stdout+stderr), ou ""> <comando...>
confere() {
  local descricao="$1" codigo_esperado="$2" trecho="$3"; shift 3
  total=$((total + 1))
  local msg codigo motivo=""
  msg="$("$@" 2>&1)"
  codigo=$?
  msg="$(grep -v '^Picked up JAVA_TOOL_OPTIONS' <<< "$msg")"
  if [ "$codigo" != "$codigo_esperado" ]; then
    motivo="saiu com $codigo, esperava $codigo_esperado"
  elif [ -n "$trecho" ] && ! grep -qF -- "$trecho" <<< "$msg"; then
    motivo="esperava a saída conter: $trecho"
  fi
  if [ -z "$motivo" ]; then
    echo "[ OK ]  $descricao"
  else
    falhas=$((falhas + 1))
    echo "[FALHA] $descricao — $motivo"
    sed 's/^/        /' <<< "$msg"
  fi
}

confere "--version"                     0 "padolang "                  $P --version
confere "--help"                        0 "Comandos:"                  $P --help
confere "check válido"                  0 "sem erros"                  $P check exemplos/primos.padoin
confere "check --json válido"           0 "[]"                         $P check --json exemplos/primos.padoin
confere "check --json R14"              1 '"codigo":"R14"'             $P check --json testes/05_erro_semantico_divisao_por_zero.padoin
confere "check --json posição do R14"   1 '"linha":5,"coluna":9,"tamanho":1' $P check --json testes/05_erro_semantico_divisao_por_zero.padoin
confere "check --json léxico"           1 '"fase":"LEXICO"'            $P check --json testes/03_erro_lexico.padoin
confere "check --json sintático S1"     1 '"codigo":"S1"'              $P check --json testes/04_erro_sintatico.padoin
confere "check mostra padoERRO"         1 "padoERRO semântico"         $P check testes/05_erro_semantico_divisao_por_zero.padoin
confere "build -o"                      0 "C gerado em saida/cli_ola.c" $P build exemplos/ola_mundo.padoin -o saida/cli_ola.c
confere "tokens"                        0 "== TOKENS =="               $P tokens exemplos/ola_mundo.padoin
confere "tokens --editor"               0 "COMENTARIO"                 $P tokens --editor testes/lexer_editor/comentarios.padoin
confere "ast"                           0 "== AST =="                  $P ast exemplos/ola_mundo.padoin
confere "forma antiga"                  0 "C gerado em saida/cli_antiga.c" $P exemplos/ola_mundo.padoin -o saida/cli_antiga.c
confere "sem argumentos"                2 "Uso:"                       $P
confere "comando desconhecido"          2 "comando desconhecido"       $P compila exemplos/primos.padoin
confere "opção desconhecida"            2 "opção desconhecida"         $P check --xyz exemplos/primos.padoin
confere "arquivo inexistente"           2 "arquivo não encontrado"     $P check nao_existe.padoin

# todo erro semântico da suíte sai com um código R1..R21 no --json
for f in testes/05_erro_semantico_*.padoin; do
  confere "código R em $(basename "$f")" 1 '"codigo":"R' $P check --json "$f"
done

echo
echo "$((total - falhas)) de $total casos passaram."
[ $falhas -eq 0 ]

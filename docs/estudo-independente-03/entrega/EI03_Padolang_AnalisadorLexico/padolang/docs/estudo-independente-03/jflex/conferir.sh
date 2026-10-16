#!/usr/bin/env bash
# Confere a especificação em JFlex (padolang.flex) contra o analisador léxico escrito à mão.
#
# Gera o scanner com o JFlex, compila, e compara a listagem de tokens dele com a do Lexer.java
# (modo editor: comentários e trechos inválidos também viram tokens):
#   - em todos os .padoin do repositório (testes/, exemplos/ e subpastas);
#   - em entradas aleatórias (ComparacaoAleatoria.java).
# A comparação é de classe, lexema, linha e coluna de cada token.
#
# Precisa do JFlex 1.9.1 e do runtime do CUP no classpath, na variável JFLEX_CP:
#   JFLEX_CP=jflex-1.9.1.jar:java-cup-runtime-11b-20160615-3.jar ./conferir.sh
# (os dois jars estão no Maven Central: de.jflex:jflex e com.github.vbmacher:java-cup-runtime).
# Sai com código 1 se houver alguma diferença.
set -e
AQUI="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$(cd "$AQUI/../../.." && pwd)"
ALEATORIAS="${ALEATORIAS:-300000}"

if [ -z "$JFLEX_CP" ]; then
  echo "defina JFLEX_CP com os jars do JFlex e do java-cup-runtime" >&2
  exit 2
fi

cd "$RAIZ"
./compilar.sh > /dev/null
GERADO="$(mktemp -d)"
trap 'rm -rf "$GERADO"' EXIT

java -cp "$JFLEX_CP" jflex.Main -q -d "$GERADO" "$AQUI/padolang.flex"
javac -encoding UTF-8 -cp out -d "$GERADO" "$GERADO/EspecificacaoLexica.java" "$AQUI/ComparacaoAleatoria.java"

total=0
diferentes=0
while IFS= read -r f; do
  total=$((total + 1))
  # do Lexer.java saem o título, a coluna TAM e as linhas de erro, que o scanner do JFlex não imprime
  manual="$(java -cp out padolang.Main "$f" --tokens --editor 2> /dev/null \
            | grep -Ev '^(== TOKENS|L[0-9] padoERRO)' | sed -E 's/^(.{12}).{5}/\1/')"
  jflex="$(java -cp "$GERADO" EspecificacaoLexica "$f")"
  if [ "$manual" != "$jflex" ]; then
    diferentes=$((diferentes + 1))
    echo "[FALHA] $f"
    diff <(echo "$manual") <(echo "$jflex") | head -n 6 | sed 's/^</        Lexer.java:/; s/^>/        JFlex:     /'
  fi
done < <(find testes exemplos -name '*.padoin' 2> /dev/null | sort)

echo "$((total - diferentes)) de $total arquivos .padoin com a mesma listagem de tokens"
java -cp "out:$GERADO" ComparacaoAleatoria "$ALEATORIAS" || diferentes=$((diferentes + 1))
[ $diferentes -eq 0 ]

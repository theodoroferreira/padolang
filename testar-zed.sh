#!/usr/bin/env bash
# Confere a gramática Tree-sitter (editores/tree-sitter-padolang/) e a extensão do Zed (editores/zed/):
#   - o src/parser.c commitado corresponde ao grammar.js (tree-sitter generate não muda nada);
#   - tree-sitter test: árvores de test/corpus/ e cores de test/highlight/;
#   - todo testes/*.padoin e exemplos/*.padoin que passa no léxico é lido sem nó ERROR;
#   - o highlights.scm da extensão é igual ao da gramática;
#   - extension.toml, config.toml, .zed/settings.json e .zed/tasks.json são válidos e usam o mesmo
#     nome de linguagem ("Padolang") e de gramática ("padolang");
#   - o rev do extension.toml é um commit que já tem a gramática atual (é de lá que o Zed a baixa);
#   - a extensão compila para wasm32-wasip2.
# Sem o tree-sitter (npm ci em editores/tree-sitter-padolang) ou sem o cargo, os casos que precisam
# deles são pulados; com EXIGIR_TUDO=1 (no CI), contam como falha.
# Sai com código 1 se algum caso falhar.
cd "$(dirname "$0")"
./compilar.sh > /dev/null || exit 1

P=bin/padolang
G=editores/tree-sitter-padolang
Z=editores/zed
TS="$PWD/$G/node_modules/.bin/tree-sitter"
CARGO="$(command -v cargo || echo "$HOME/.cargo/bin/cargo")"

total=0
falhas=0
ok() { total=$((total + 1)); echo "[ OK ]  $1"; }
falha() { total=$((total + 1)); falhas=$((falhas + 1)); echo "[FALHA] $1"; }
pulado() {
  if [ "${EXIGIR_TUDO:-0}" = 1 ]; then falha "$1 — $2"; else echo "[PULADO] $1 — $2"; fi
}

# ---------------------------------------------------------------- gramática Tree-sitter

if [ -x "$TS" ]; then
  # generate reescreve o src/: se algo mudar, o parser.c commitado estava desatualizado
  antes="$(cat "$G/src/parser.c" "$G/src/grammar.json" "$G/src/node-types.json" | cksum)"
  if (cd "$G" && "$TS" generate > /dev/null 2>&1); then
    depois="$(cat "$G/src/parser.c" "$G/src/grammar.json" "$G/src/node-types.json" | cksum)"
    if [ "$antes" = "$depois" ]; then
      ok "src/parser.c corresponde ao grammar.js"
    else
      falha "src/parser.c estava desatualizado (já regenerado: commite o $G/src/)"
    fi
  else
    falha "tree-sitter generate"
  fi

  if saida="$(cd "$G" && "$TS" test 2> /dev/null)"; then
    ok "tree-sitter test ($(grep -c '✓' <<< "$saida") casos: árvores e cores)"
  else
    falha "tree-sitter test"
    grep '✗\|Failure' <<< "$saida" | sed 's/^/        /'
  fi

  lidos=0
  for f in testes/*.padoin exemplos/*.padoin testes/formatador/*.padoin testes/formatador/*.formatado; do
    $P tokens "$f" > /dev/null 2>&1 || continue   # com erro léxico, um ERROR na árvore é esperado
    lidos=$((lidos + 1))
    erros="$(cd "$G" && "$TS" parse -q "../../$f" 2> /dev/null)"
    [ -n "$erros" ] && falha "$f é lido com erro pela gramática: ${erros##*$'\t'}"
  done
  ok "$lidos programas de testes/ e exemplos/ lidos pela gramática (os sem erro léxico)"

  # queries da extensão (brackets, indents, outline, ...): nomes de nós e campos existem na gramática
  for q in "$Z"/languages/padolang/*.scm; do
    if erro="$(cd "$G" && "$TS" query "../../$q" ../../testes/10_valido_lacos.padoin 2>&1 > /dev/null | grep -v 'parser directories\|init-config\|configuration file\|language grammars' | grep .)"; then
      falha "$q: $erro"
    else
      ok "$q vale para a gramática"
    fi
  done
else
  pulado "gramática Tree-sitter" "rode 'npm ci' em $G"
fi

# ---------------------------------------------------------------- extensão do Zed

if cmp -s "$G/queries/highlights.scm" "$Z/languages/padolang/highlights.scm"; then
  ok "highlights.scm da extensão é igual ao da gramática"
else
  falha "highlights.scm da extensão é diferente do da gramática (copie $G/queries/highlights.scm)"
fi

# Configurações: TOML pelo tomllib, e o JSON do Zed aceita comentários de linha inteira (//)
if msg="$(python3 - "$Z" << 'EOF'
import json, re, sys, tomllib
z = sys.argv[1]
ext = tomllib.load(open(f"{z}/extension.toml", "rb"))
lang = tomllib.load(open(f"{z}/languages/padolang/config.toml", "rb"))
def jsonc(p):
    return json.loads(re.sub(r"(?m)^\s*//.*$", "", open(p, encoding="utf-8").read()))
settings = jsonc(".zed/settings.json")
tasks = jsonc(".zed/tasks.json")

problemas = []
for campo in ("id", "name", "version", "schema_version", "authors", "description", "repository"):
    if campo not in ext:
        problemas.append(f"extension.toml sem '{campo}'")
g = ext.get("grammars", {}).get("padolang", {})
for campo in ("repository", "rev", "path"):
    if campo not in g:
        problemas.append(f"[grammars.padolang] sem '{campo}'")
if lang.get("grammar") != "padolang":
    problemas.append("config.toml: grammar deveria ser 'padolang'")
if "padoin" not in lang.get("path_suffixes", []):
    problemas.append("config.toml: path_suffixes sem 'padoin'")
servidor = ext.get("language_servers", {}).get("padolang-lsp", {})
if servidor.get("languages") != [lang.get("name")]:
    problemas.append("language_servers.padolang-lsp.languages diferente do name do config.toml")
if lang.get("name") not in settings.get("languages", {}):
    problemas.append(".zed/settings.json sem a linguagem " + str(lang.get("name")))
if not isinstance(tasks, list) or not all("label" in t and "command" in t for t in tasks):
    problemas.append(".zed/tasks.json: cada tarefa precisa de label e command")
if problemas:
    print("; ".join(problemas))
    sys.exit(1)
print(f"{g['rev']} {g['path']}")
EOF
)"; then
  ok "extension.toml, config.toml e .zed/*.json válidos e coerentes"
  rev="${msg%% *}"
  caminho="${msg#* }"
  if ! git cat-file -e "$rev^{commit}" 2> /dev/null; then
    falha "rev $rev do extension.toml não existe neste repositório (no CI, o checkout precisa de fetch-depth: 0)"
  elif ! git diff --quiet "$rev" -- "$caminho/grammar.js" "$caminho/src"; then
    falha "rev $rev do extension.toml tem uma gramática diferente da atual: commite a gramática e atualize o rev"
  else
    ok "rev ${rev:0:7} do extension.toml tem a gramática atual"
  fi
else
  falha "configuração da extensão: $msg"
fi

if [ -x "$CARGO" ]; then
  if saida="$(cd "$Z" && "$CARGO" build --release --target wasm32-wasip2 2>&1)"; then
    if grep -q '^warning' <<< "$saida"; then
      falha "extensão compila, mas com avisos do Rust"
      grep -A5 '^warning' <<< "$saida" | sed 's/^/        /'
    else
      ok "extensão compila para wasm32-wasip2"
    fi
  else
    falha "extensão não compila para wasm32-wasip2"
    tail -20 <<< "$saida" | sed 's/^/        /'
  fi
else
  pulado "compilar a extensão" "instale o Rust com rustup e rode 'rustup target add wasm32-wasip2'"
fi

echo
echo "$((total - falhas)) de $total casos passaram."
[ $falhas -eq 0 ]

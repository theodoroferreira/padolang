#!/usr/bin/env bash
# Confere o servidor LSP (padolang lsp).
#
# Envia a sessão gravada em testes/lsp/sessao.jsonl (uma mensagem por linha; este script acrescenta
# o cabeçalho Content-Length, contado em bytes) e confere:
#   - o stdout só tem mensagens do protocolo, cada uma com o Content-Length certo em bytes;
#   - as respostas são exatamente as de testes/lsp/sessao.esperado (uma por linha);
#   - os pontos principais, um a um (diagnóstico R14 com o range em UTF-16, tokens semânticos, ...);
#   - o processo sai com 0 depois de shutdown + exit, e com 1 num exit sem shutdown.
# Sai com código 1 se algum caso falhar.
cd "$(dirname "$0")"
./compilar.sh > /dev/null || exit 1
export LC_ALL=C            # ${#var} e ${var:i:n} contam bytes, como o Content-Length

P=bin/padolang
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

total=0
falhas=0
ok() { total=$((total + 1)); echo "[ OK ]  $1"; }
falha() { total=$((total + 1)); falhas=$((falhas + 1)); echo "[FALHA] $1"; }

# enquadrar <arquivo.jsonl>: cada linha (menos vazias e comentários) vira uma mensagem com cabeçalho
enquadrar() {
  local linha
  while IFS= read -r linha || [ -n "$linha" ]; do
    [ -z "$linha" ] && continue
    [[ "$linha" == \#* ]] && continue
    printf 'Content-Length: %d\r\n\r\n%s' "${#linha}" "$linha"
  done < "$1"
}

# desenquadrar <arquivo>: imprime o corpo de cada mensagem, um por linha; falha se algo não bate
desenquadrar() {
  local resto cabecalho=$'^Content-Length: ([0-9]+)\r\n\r\n' n
  resto="$(cat "$1"; printf x)"
  resto="${resto%x}"
  while [ -n "$resto" ]; do
    if ! [[ "$resto" =~ $cabecalho ]]; then
      echo "saída fora do protocolo: ${resto:0:80}"
      return 1
    fi
    resto="${resto:${#BASH_REMATCH[0]}}"
    n="${BASH_REMATCH[1]}"
    if [ "${#resto}" -lt "$n" ]; then
      echo "mensagem cortada: Content-Length $n, mas só restam ${#resto} bytes"
      return 1
    fi
    printf '%s\n' "${resto:0:n}"
    resto="${resto:n}"
  done
}

# ------------------------------------------------------------------ a sessão gravada

enquadrar testes/lsp/sessao.jsonl | $P lsp > "$TMP/saida" 2> "$TMP/log"
codigo=$?

if [ $codigo -eq 0 ]; then
  ok "sai com 0 depois de shutdown e exit"
else
  falha "sai com 0 depois de shutdown e exit — saiu com $codigo"
  sed 's/^/        /' "$TMP/log"
fi

if desenquadrar "$TMP/saida" > "$TMP/mensagens"; then
  ok "stdout só com mensagens do protocolo, Content-Length em bytes ($(wc -l < "$TMP/mensagens") mensagens)"
else
  falha "stdout só com mensagens do protocolo — $(cat "$TMP/mensagens")"
fi

if diff -q testes/lsp/sessao.esperado "$TMP/mensagens" > /dev/null; then
  ok "respostas iguais a testes/lsp/sessao.esperado"
else
  falha "respostas diferentes de testes/lsp/sessao.esperado"
  diff testes/lsp/sessao.esperado "$TMP/mensagens" | sed 's/^</        esperado:/; s/^>/        obtido:  /'
fi

# confere <descrição> <trecho que alguma mensagem precisa conter>
confere() {
  if grep -qF -- "$2" "$TMP/mensagens"; then ok "$1"; else falha "$1 — nenhuma mensagem contém: $2"; fi
}

confere "initialize anuncia sync, semantic tokens e formatação" \
  '"capabilities":{"textDocumentSync":1,"semanticTokensProvider":{"legend":{"tokenTypes":["keyword","type","function","parameter","variable","string","number","operator","comment"],"tokenModifiers":["declaration"]},"full":true},"documentFormattingProvider":true}'
confere "R14 sublinha o '/' depois de um 👍 na mesma linha (coluna 33 = caractere 33)" \
  '"uri":"file:///a.padoin","diagnostics":[{"range":{"start":{"line":2,"character":33},"end":{"line":2,"character":34}},"severity":1,"code":"R14","source":"padolang"'
confere "\\ud83d\\udc4d no JSON; o z da coluna 48 é o caractere 48" \
  '"uri":"file:///b.padoin","diagnostics":[{"range":{"start":{"line":1,"character":48},"end":{"line":1,"character":49}},"severity":1,"code":"R1"'
confere "erro léxico L3 (texto sem aspa) vai até o fim da linha, 👍 com 2 unidades" \
  '"range":{"start":{"line":1,"character":14},"end":{"line":1,"character":20}},"severity":1,"code":"L3"'
confere "erro sintático S1 no 👍" \
  '"range":{"start":{"line":1,"character":23},"end":{"line":1,"character":25}},"severity":1,"code":"S1"'
confere "didChange corrige: diagnósticos vazios" \
  '"uri":"file:///a.padoin","diagnostics":[]}'
confere "tokens semânticos (parâmetro, função, 👍🏽 com 4 unidades, comentário)" \
  '"id":2,"result":{"data":[0,0,10,0,0,0,11,9,1,0,0,10,1,2,1,0,2,9,1,0,0,10,1,3,1,1,2,6,0,0,0,7,1,3,0,0,2,1,7,0,0,2,1,6,0,0,1,4,7,0,2,0,6,0,0,0,9,11,0,0,0,12,1,2,0,0,2,1,6,0,0,3,2,7,0,0,5,5,8,0]}'
confere "formatação: um edit com o documento inteiro, 👍🏽 preservado" \
  '"id":3,"result":[{"range":{"start":{"line":0,"character":0},"end":{"line":4,"character":0}},"newText":"padUNCTION padoINTER f(padoINTER n) {\n  padOUT n + 1👍🏽\n}\nPADOIN {\n  padoPRINTLN(f(2))👍\n} # fim\n"}]'
confere "método desconhecido: MethodNotFound" '"id":4,"error":{"code":-32601'
confere "JSON inválido: ParseError, e o servidor continua" '"id":null,"error":{"code":-32700'
confere "documento não aberto: InvalidParams" '"id":6,"error":{"code":-32602'
confere "didClose limpa os diagnósticos" '"uri":"file:///b.padoin","diagnostics":[]}'
confere "shutdown responde null" '"id":7,"result":null}'

# ------------------------------------------------------------------ ciclo de vida

printf '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}\n{"jsonrpc":"2.0","method":"exit"}\n' > "$TMP/sem_shutdown.jsonl"
enquadrar "$TMP/sem_shutdown.jsonl" | $P lsp > /dev/null 2>&1
if [ $? -eq 1 ]; then ok "exit sem shutdown sai com 1"; else falha "exit sem shutdown sai com 1"; fi

printf '{"jsonrpc":"2.0","id":1,"method":"textDocument/semanticTokens/full","params":{}}\n{"jsonrpc":"2.0","method":"exit"}\n' > "$TMP/sem_initialize.jsonl"
enquadrar "$TMP/sem_initialize.jsonl" | $P lsp 2> /dev/null > "$TMP/saida2"
if desenquadrar "$TMP/saida2" | grep -qF '"id":1,"error":{"code":-32002'; then
  ok "requisição antes do initialize: ServerNotInitialized"
else
  falha "requisição antes do initialize: ServerNotInitialized"
fi

echo
echo "$((total - falhas)) de $total casos passaram."
[ $falhas -eq 0 ]

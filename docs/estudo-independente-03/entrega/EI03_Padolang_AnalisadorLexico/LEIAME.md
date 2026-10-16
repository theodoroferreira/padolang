# Estudo Independente 03 — Analisador léxico da Padolang

Theodoro Gaspar Ferreira e Vinícius Andrey Costa Mattos
Linguagens Formais e Compiladores

## O que há neste pacote

| Caminho | Conteúdo |
|---|---|
| `relatorio.pdf` | resolução comentada dos 4 problemas, evidências de teste e referências |
| `declaracao-uso-ia.md` | declaração de uso de IA generativa |
| `padolang/src/padolang/lexer/` | **o analisador léxico**: `Lexer.java`, `Token.java`, `TokenType.java`, `LexicoException.java` |
| `padolang/src/padolang/` (demais pastas) | o restante do compilador (parser, semântica, gerador de C, CLI), necessário para compilar e para o comando `tokens` |
| `padolang/testes/lexer_editor/` | arquivos de teste (`.padoin`) e a saída do analisador para cada um (`.tokens`) |
| `padolang/testar-lexer-editor.sh` | roda o analisador em cada teste e compara com o `.tokens` |
| `padolang/docs/estudo-independente-03/jflex/` | a especificação léxica em JFlex (`padolang.flex`) e a conferência dela contra o analisador |

Os dois arquivos de teste pedidos no Problema 4 são:

- `testes/lexer_editor/todos_os_tokens.padoin`: programa válido, com todas as classes de token;
- `testes/lexer_editor/erros_lexicos.padoin`: erros léxicos propositais (os 7 tipos de erro).

## Como compilar e executar

É preciso o JDK 21 ou superior e o bash (Linux, macOS ou WSL).

```bash
cd padolang
./compilar.sh                                                       # compila para out/ e gera out/padolang.jar

bin/padolang tokens --editor testes/lexer_editor/todos_os_tokens.padoin   # lista os tokens
bin/padolang tokens --editor testes/lexer_editor/erros_lexicos.padoin     # tokens e erros léxicos
bin/padolang tokens --editor arquivo.padoin > saida.tokens                # grava a listagem num arquivo
```

Sem o bash (Windows, por exemplo), o mesmo comando é `java -jar out/padolang.jar tokens --editor arquivo.padoin`, depois de compilar com `javac -encoding UTF-8 -d out` todos os `.java` de `src/`.

A listagem tem uma linha por token, com linha, coluna, tamanho, classe e lexema. Depois dos tokens vem uma linha por erro léxico, com o código do erro (`L1` a `L7`), a linha, a coluna, a mensagem e o lexema. O código de saída é 1 se houver algum erro léxico.

`bin/padolang tokens arquivo.padoin` (sem `--editor`) é o modo usado pelo compilador: os comentários são descartados e a análise para no primeiro erro léxico.

## Como repetir os testes

```bash
./testar-lexer-editor.sh      # 8 de 8 casos passaram.
```

## Conferência com o JFlex

O `padolang.flex` não é usado pelo compilador: é a especificação formal dos tokens, escrita na notação do JFlex para poder ser executada. O `conferir.sh` gera o scanner, compila e compara a listagem de tokens dele com a do `Lexer.java`, nos arquivos de teste e em 300 000 entradas aleatórias:

```bash
JFLEX_CP=jflex-1.9.1.jar:java-cup-runtime-11b-20160615-3.jar docs/estudo-independente-03/jflex/conferir.sh
```

Os dois jars estão no Maven Central (`de.jflex:jflex:1.9.1` e `com.github.vbmacher:java-cup-runtime:11b-20160615-3`). O resultado da execução feita para o relatório está em `conferencia.saida`. No repositório completo a comparação cobre 75 programas; neste pacote, os 8 de `testes/lexer_editor/`.

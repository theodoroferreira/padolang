# padolang👍

**padolang** é uma linguagem de programação orientada a Edson Luiz Padoin (GOAT), com palavras reservadas construídas a partir de *pad*, *pado* e *padoin* e com o 👍 no lugar do ponto e vírgula. O repositório contém o **transpilador padolang → C**, escrito em Java.

```
PADOIN {
  padoin padoSTRING x = "hello world"👍
  padoPRINTLN(x)👍
}
```

```
$ ./padolang.sh exemplo.padoin
C gerado em saida/exemplo.c
---------- executando exemplo ----------
hello world
```

## Como funciona

```
arquivo.padoin → LÉXICO → TOKENS → PARSER → AST → SEMÂNTICA → CÓDIGO C → gcc → executável
```

| Etapa | Onde está | O que faz |
|---|---|---|
| Análise léxica | `src/padolang/lexer/` | transforma o texto em tokens, com linha e coluna |
| Análise sintática | `src/padolang/parser/` | parser por descida recursiva, com um método por produção da gramática; constrói a AST |
| AST | `src/padolang/ast/` | árvore sintática abstrata (records Java) |
| Análise semântica | `src/padolang/semantica/` | tabela de funções, tabela de símbolos com pilha de escopos e regras de tipo; anota o tipo de cada expressão |
| Geração de código | `src/padolang/gerador/` | percorre a AST e emite C, com as funções auxiliares de execução que o programa usar |

## Pré-requisitos

- **JDK 21** ou superior
- **gcc**
- **bash** (Linux, macOS ou WSL)

No Ubuntu:

```bash
sudo apt install openjdk-21-jdk gcc
```

## Compilação e execução

```bash
git clone https://github.com/theodoroferreira/padolang.git
cd padolang

./compilar.sh                                  # compila o transpilador em out/
./padolang.sh exemplos/tabuada.padoin          # transpila, compila e executa
```

O `padolang.sh` é um atalho para `bin/padolang run --dir saida` (ver abaixo): recompila o transpilador se algum `.java` mudou, gera `saida/<nome>.c`, compila com `gcc -std=c11 -fwrapv` e executa o programa. Opções:

```bash
./padolang.sh arquivo.padoin --tokens   # mostra a tabela de tokens
./padolang.sh arquivo.padoin --ast      # mostra a árvore sintática
```

Para só gerar o C, sem compilar nem executar:

```bash
java -cp out padolang.Main arquivo.padoin -o saida.c
```

### CLI (`bin/padolang`)

O `compilar.sh` também empacota o transpilador em `out/padolang.jar`. O `bin/padolang` (ou `bin/padolang.cmd`, no Windows) chama esse jar e o recompila sozinho se algum `.java` mudou:

```bash
bin/padolang check  arquivo.padoin [--json]      # confere o programa, sem gerar C
bin/padolang build  arquivo.padoin [-o saida.c]  # gera o C
bin/padolang run    arquivo.padoin [--manter]    # gera o C, compila e executa
bin/padolang fmt    arquivo.padoin... [--check | --stdout]  # formata os arquivos
bin/padolang lsp                                 # servidor LSP (stdin/stdout), usado pelos editores
bin/padolang tokens arquivo.padoin [--editor]    # lista os tokens
bin/padolang ast    arquivo.padoin               # mostra a árvore sintática
bin/padolang --help | --version
```

O `check --json` imprime os erros com fase, código (`L1`..`L6`, `S1`, `R1`..`R21`), linha, coluna e tamanho, por exemplo `[{"fase":"SEMANTICO","severidade":"ERRO","codigo":"R14","linha":5,"coluna":9,"tamanho":1,"mensagem":"divisão por zero"}]`. Códigos de saída: 0 ok, 1 erro no programa, 2 erro de uso ou de arquivo.

O `run` usa o compilador C da variável `PADOLANG_CC`, se existir; senão o `gcc`; senão o `cc`. O `.c` e o executável ficam numa pasta temporária, apagada no fim; com `--manter` eles ficam em `saida/`, e com `--dir <pasta>`, na pasta indicada. O programa lê do teclado (ou da entrada redirecionada) e o código de saída é o dele: 1 num `padoERRO de execução`.

O `fmt` formata no próprio arquivo: 2 espaços por bloco, um comando por linha, `{` na mesma linha, `} oin {`, espaços em volta dos operadores binários, comentários preservados e no máximo uma linha em branco seguida. Com `--stdout` só imprime o resultado; com `--check` só confere e sai com 1 se algum arquivo mudaria. Um arquivo com erro léxico ou sintático não é alterado, e o formatador confere que o resultado tem exatamente os mesmos tokens do original.

O `lsp` é um servidor [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) sem dependências (JSON e JSON-RPC escritos à mão): mostra os erros no editor enquanto se digita (com o código `L`, `S` ou `R` e o trecho sublinhado), dá as cores por Semantic Tokens (parâmetros diferentes de variáveis, por exemplo) e formata o documento com o mesmo formatador do `fmt`. As colunas da padolang contam code points e as do LSP contam unidades UTF-16; a conversão fica em `lsp/Posicoes.java` (um 👍 é 1 coluna na padolang e 2 no editor).

Para usar só `padolang`, coloque a pasta `bin/` no `PATH` (é por ele que os editores vão achar a padolang):

```bash
export PATH="$PWD/bin:$PATH"     # no ~/.bashrc ou ~/.zshrc, com o caminho completo
```

### Extensão VS Code / Cursor (`editores/vscode/`)

Cores (gramática TextMate e Semantic Tokens), erros sublinhados enquanto se digita, formatação (`Shift+Alt+F`) e o comando "Padolang: Executar arquivo", que roda o programa num terminal integrado com entrada pelo teclado. Precisa do Java 21. Para gerar e instalar:

```bash
./compilar.sh
cd editores/vscode && npm install && npm run empacotar   # gera padolang-0.1.0.vsix, com o jar dentro
code --install-extension padolang-0.1.0.vsix             # ou "Extensions: Install from VSIX..." no VS Code ou no Cursor
```

Detalhes e configurações em [`editores/vscode/README.md`](editores/vscode/README.md).

### Extensão Zed (`editores/zed/`)

Registra a linguagem "Padolang" no Zed (gramática Tree-sitter em `editores/tree-sitter-padolang/`) e inicia o `padolang lsp`: cores por Semantic Tokens, erros sublinhados e formatação ao salvar. Precisa do Java 21, do `bin/` no `PATH` (ou do repositório da padolang aberto no Zed) e do Rust instalado pelo [rustup](https://rustup.rs), porque o Zed compila a extensão. Para instalar, no Zed: "zed: install dev extension" e escolher a pasta `editores/zed/`.

Com este repositório aberto, o `.zed/settings.json` liga as cores do LSP (`"semantic_tokens": "full"`) e a formatação ao salvar, e o `.zed/tasks.json` traz as tarefas "padolang: executar", "conferir", "gerar C", "formatar" e "testar" ("task: spawn", ou o botão ▶ ao lado do `PADOIN`). Detalhes em [`editores/zed/README.md`](editores/zed/README.md).

## A linguagem

Os arquivos têm a extensão `.padoin`. Todo programa começa com `PADOIN { ... }`, e as funções são declaradas antes do bloco principal. Quando uma escolha não é temática, a padolang segue o **Java**; as decisões estão registradas em [`docs/decisoes.md`](docs/decisoes.md).

| Construção | padolang | C gerado |
|---|---|---|
| Início do programa | `PADOIN { ... }` | `int main(void) { ... }` |
| Fim de comando | `👍` | `;` |
| Declaração | `padoin padoINTER x = 1👍` | `int pad_x = 1;` |
| Atribuição | `x = x + 1👍` / `x++👍` | `pad_x = pad_x + 1;` / `pad_x++;` |
| Saída | `padoPRINT(x)👍` / `padoPRINTLN(x)👍` | `printf("%d", pad_x);` / `printf("%d\n", pad_x);` |
| Entrada | `padoIN(x)👍` | `pad_x = padolang_ler_inter(linha);` |
| Condicional | `pad (x > 1) { } oin { }` | `if (...) { } else { }` |
| Senão-se | `oin pad (x > 0) { }` | `else if (...) { }` |
| Repetição | `papapadoin (i = 0, i < 10, i++) { }` | `for (...; ...; ...) { }` |
| Enquanto | `padoinquanto (x > 0) { }` | `while (...) { }` |
| Sair / próxima volta | `padBREAK👍` / `padCONTINUE👍` | `break;` / `continue;` |
| Função | `padUNCTION padoINTER soma(padoINTER a, padoINTER b) { }` | `int pad_soma(int pad_a, int pad_b) { }` |
| Retorno | `padOUT a + b👍` | `return pad_a + pad_b;` |
| Chamada como comando | `mostra(3)👍` | `pad_mostra(3);` |
| Comentário | `# até o fim da linha` | — |

### Tipos

| padolang | C | Literais |
|---|---|---|
| `padoINTER` | `int` | `42` |
| `padoFLOAT` | `double` | `3.14` |
| `padoOLEAN` | `bool` | `padoTRUE`, `padoFALSE` |
| `padoSTRING` | `char *` | `"texto\n"` (escapes `\n`, `\t`, `\"`, `\\`) |

Conversão explícita de real para inteiro: `padoINTER(7.8)` vale `7` (trunca em direção a zero). De inteiro para real a conversão é automática.

### Operadores

Da precedência mais forte para a mais fraca, como no Java:

| Operadores | Observação |
|---|---|
| `-x`, `padNOT x` | menos unário e negação lógica |
| `*` `/` `padoMOD` | entre inteiros, `/` é divisão inteira; `padoMOD` é o resto, só entre inteiros |
| `+` `-` | `+` com um texto de um dos lados concatena: `"Nota: " + 7.5` |
| `<` `<=` `>` `>=` | entre números ou entre textos (ordem alfabética) |
| `==` `!=` | entre números, textos (compara o conteúdo) ou lógicos |
| `padAND` | "e", com curto-circuito |
| `padOR` | "ou", com curto-circuito |

Comparações e lógicos produzem um `padoOLEAN`, que pode ser guardado em variável, retornado ou usado direto como condição: `padoin padoOLEAN adulto = idade >= 18👍` e depois `pad (adulto) { }`.

### Entrada e saída

- `padoPRINT` não pula linha; `padoPRINTLN` pula, e `padoPRINTLN()👍` só pula a linha.
- Reais são escritos como no Java: `7.0`, `3.3333333333333335`, `1.2345678E7`.
- `padoIN` lê uma linha por vez: um número por linha para `padoINTER`/`padoFLOAT`, `padoTRUE` ou `padoFALSE` para `padoOLEAN`, e a linha inteira (com espaços, podendo ser vazia) para `padoSTRING`.

### Erros de execução

O que só pode ser descoberto ao rodar faz o programa terminar com uma mensagem no mesmo padrão das outras fases e código de saída 1:

```
padoERRO de execução (linha 7): divisão por zero
```

Isso vale para divisão ou `padoMOD` inteiro por zero, entrada inválida ou fim da entrada no `padoIN`, e `padoINTER(x)` com um valor fora da faixa. O estouro de inteiro não é erro: o valor dá a volta, como no Java (`2147483647 + 1` vale `-2147483648`).

### Observações

- As palavras reservadas diferenciam maiúsculas de minúsculas: `PADOIN` (programa), `padoin` (declaração) e `padoIN` (entrada) são tokens diferentes.
- Uma variável declarada sem valor começa com o valor nulo do tipo: `0`, `0.0`, `padoFALSE` ou `""`.
- No C gerado, todo identificador recebe o prefixo `pad_`. Assim nomes como `int` ou `main` continuam válidos na padolang.
- No `papapadoin`, a inicialização e o passo são opcionais.
- `5--3` é erro sintático: pelo maior casamento, `--` é o decremento (como no Java e no C). Escreva `5 - -3`.
- A memória dos textos criados por concatenação e leitura não é liberada; os programas padolang são curtos.

A especificação formal (alfabeto Σ, tabela de tokens e gramática em EBNF) está no relatório técnico. As regras semânticas estão em [`docs/semantica.md`](docs/semantica.md).

### Regras semânticas, em resumo

- Toda variável é declarada antes do uso e só existe no bloco em que foi declarada; funções não enxergam as variáveis do `PADOIN`.
- Um nome visível não pode ser declarado de novo, e variáveis não podem ter o nome de uma função.
- `padoINTER` pode ir para `padoFLOAT`, mas `padoFLOAT` só vai para `padoINTER` com `padoINTER(x)`.
- Aritmética só com números (exceto `+` com texto); lógicos só com `padoOLEAN`; condições precisam ser `padoOLEAN`.
- Chamadas conferem se a função existe, o número de argumentos e o tipo de cada um.
- `padOUT` só dentro de função, com o tipo de retorno certo, e toda função precisa retornar em todos os caminhos.
- `padBREAK` e `padCONTINUE` só dentro de laço, e nenhum comando pode vir depois deles ou de um `padOUT` no mesmo bloco.

A semântica mostra todos os erros encontrados, um por linha, com linha e coluna.

## Testes

```bash
./testar.sh        # resultado de cada caso
./testar.sh -v     # também mostra a saída dos programas e as mensagens de erro
```

O script roda todos os programas de `testes/` e `exemplos/`, e o que ele exige depende do nome do arquivo:

- `erro_execucao` no nome: o programa precisa compilar e terminar com `padoERRO de execução`;
- outro `erro` no nome: o transpilador precisa **rejeitar** o programa, com mensagem que começa com `padoERRO`;
- nos dois casos, uma linha `# espera-erro: <trecho>` no arquivo exige que a mensagem contenha esse trecho, o que garante que o erro apontado é o certo;
- os demais precisam transpilar, compilar e executar sem falha; se existir `<nome>.saida`, a saída precisa ser exatamente igual a ela;
- se existir `<nome>.entrada`, ele é usado como entrada do programa;
- cada programa tem no máximo 10 segundos, para um laço infinito não travar a suíte.

| Arquivo | Objetivo |
|---|---|
| `01_valido_basico` | declaração, atribuição e saída |
| `02_valido_completo` | condicional, repetição, entrada/saída, função e precedência |
| `03_erro_lexico` | caractere que não pertence à linguagem |
| `04_erro_sintatico` | declaração sem expressão após `=` |
| `05_erro_semantico_*` | um arquivo por regra semântica (R1 a R21, ver `docs/semantica.md`) |
| `06_valido_semantica` | casos que a semântica precisa aceitar (`padoINTER` → `padoFLOAT`, blocos irmãos, chamada a função declarada depois) |
| `07_erro_execucao_*` | erros que só aparecem ao rodar: divisão por zero, entrada inválida, fim da entrada, conversão fora da faixa |
| `08_valido_expressoes` | lógicos, precedência, curto-circuito, `padoMOD`, `padoINTER(x)`, estouro e escrita de reais |
| `09_valido_textos` | concatenação, comparação de textos, leitura da linha inteira, trigrafias |
| `10_valido_lacos` | `padoinquanto`, `padBREAK`, `padCONTINUE` e chamada como comando |
| `exemplo_funcao` | função com dois parâmetros e condicional |
| `exemplo_textos_e_recursao` | textos, lógicos, recursão e expressões com menos unário |

A cada *push* ou *pull request* na `main` ou na `ferramentas`, o GitHub Actions compila o projeto e roda o `testar.sh`, o `testar-lexer-editor.sh`, o `testar-cli.sh`, o `testar-formatador.sh` e o `testar-lsp.sh`; depois confere a gramática da extensão VS Code e empacota o `.vsix`, e roda o `testar-zed.sh` (gramática Tree-sitter, queries e compilação da extensão do Zed). O C gerado e o `.vsix` ficam disponíveis como artefatos da execução.

## Estrutura do repositório

```
padolang/
├── src/padolang/        código do transpilador
│   ├── lexer/
│   ├── parser/
│   ├── ast/
│   ├── semantica/
│   ├── gerador/
│   ├── diagnostico/     erros estruturados (fase, código, posição, tamanho)
│   ├── cli/             subcomandos da linha de comando
│   ├── runner/          chama o compilador C e executa o programa
│   ├── formatador/      padolang fmt (a partir dos tokens do modo editor)
│   ├── lsp/             padolang lsp: JSON, JSON-RPC, diagnósticos, Semantic Tokens, posições UTF-16
│   └── Main.java
├── testes/              suíte obrigatória (válidos e inválidos); formatador/, lexer_editor/ e lsp/
├── exemplos/            programas de demonstração
├── docs/                decisões de projeto e regras semânticas
├── bin/                 lançadores da CLI (padolang, padolang.cmd)
├── editores/vscode/     extensão VS Code / Cursor (gramática TextMate + cliente LSP)
├── editores/tree-sitter-padolang/  gramática Tree-sitter mínima (exigida pelo Zed)
├── editores/zed/        extensão Zed (queries + lançador do LSP em Rust)
├── .zed/                configurações e tarefas do Zed para este repositório
├── compilar.sh          compila o transpilador e gera out/padolang.jar
├── padolang.sh          transpila, compila e executa um programa (atalho para bin/padolang run)
├── testar.sh            roda todos os testes e exemplos
├── testar-lexer-editor.sh  confere o léxico no modo editor
├── testar-formatador.sh confere o formatador (testes/formatador/, idempotência, comportamento)
├── testar-lsp.sh        confere o servidor LSP com a sessão gravada em testes/lsp/
├── testar-zed.sh        confere a gramática Tree-sitter e a extensão do Zed
└── testar-cli.sh        confere a CLI (subcomandos, --json, run e códigos de saída)
```

## Integrantes

- Andrei Renan de Conto Albrecht - andrei.albrecht@sou.unijui.edu.br
- Alexandre Tonin Mota Rico - alexandre.rico@sou.unijui.edu.br
- Theodoro Gaspar Ferreira - theodoro.ferreira@sou.unijui.edu.br
- Vinícius Andrey Costa Mattos - vinicius.mattos@sou.unijui.edu.br

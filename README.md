# padolang👍

**padolang** é uma linguagem de programação orientada a Edson Luiz Padoin (GOAT), com palavras reservadas construídas a partir de *pad*, *pado* e *padoin* e com o 👍 no lugar do ponto e vírgula. O repositório contém o **transpilador padolang → C**, escrito em Java.

```
PADOIN {
  padoin padoSTRING x = "hello world"👍
  padoPRINTLN(x)👍
}
```

```
$ padolang run exemplo.padoin
---------- executando exemplo ----------
hello world
```

[Começando](#começando) · [Referência da linguagem](#referência-da-linguagem) · [Linha de comando](#linha-de-comando) · [Desenvolvimento](#desenvolvimento)

## Começando

### 1. Instale

A padolang precisa de duas coisas no computador: o **Java 21** ou mais novo, que roda o transpilador, e o **gcc**, que compila o C gerado.

| Sistema | Como instalar |
|---|---|
| Ubuntu / Debian | `sudo apt install openjdk-21-jre gcc` |
| macOS | `brew install openjdk@21` e `xcode-select --install` (traz o `gcc`) |
| Windows | Java do [Adoptium](https://adoptium.net/) e gcc do [MSYS2](https://www.msys2.org/) (`pacman -S mingw-w64-ucrt-x86_64-gcc`, com a pasta `ucrt64\bin` no `Path`) |

Depois baixe o `padolang-0.1.0.zip` na página de [Releases](https://github.com/theodoroferreira/padolang/releases), descompacte e confira:

```bash
unzip padolang-0.1.0.zip
cd padolang-0.1.0
bin/padolang --version          # padolang 0.1.0
```

Para chamar só `padolang`, de qualquer pasta, coloque o `bin/` no `PATH`:

- **Linux / macOS**: acrescente ao `~/.bashrc` (ou `~/.zshrc`) a linha abaixo, com o caminho completo, e abra um terminal novo:
  ```bash
  export PATH="$HOME/padolang-0.1.0/bin:$PATH"
  ```
- **Windows**: em "Editar as variáveis de ambiente do sistema" → `Path` → Novo, adicione a pasta `bin` (por exemplo `C:\padolang-0.1.0\bin`). O comando é o `padolang.cmd`, chamado também como `padolang`.

### 2. Escreva o primeiro programa

Crie um arquivo `ola.padoin`:

```
PADOIN {
  padoPRINTLN("Olá, padolang!")👍
}
```

E rode:

```
$ padolang run ola.padoin
---------- executando ola ----------
Olá, padolang!
```

O `run` faz tudo de uma vez: confere o programa, gera o C, compila com o gcc e executa. Três regras já aparecem aqui:

- os arquivos têm a extensão `.padoin`;
- o programa principal fica dentro de `PADOIN { ... }`;
- todo comando termina com `👍`. Para digitar o 👍, copie e cole, ou use o seletor de emoji do sistema (`Win` + `.` no Windows, `Ctrl` + `Cmd` + `Espaço` no macOS).

### 3. Um tour rápido

**Variáveis.** Toda variável é declarada com `padoin`, o tipo e o nome. Os tipos são `padoINTER` (inteiro), `padoFLOAT` (real), `padoOLEAN` (lógico) e `padoSTRING` (texto). O `+` com um texto de um dos lados concatena:

```
PADOIN {
  padoin padoSTRING nome = "Padoin"👍
  padoin padoINTER ano = 2026👍
  padoin padoFLOAT nota = 9.5👍
  padoPRINTLN("Olá, " + nome + "! Ano: " + ano + ", nota: " + nota)👍
}
```

```
Olá, Padoin! Ano: 2026, nota: 9.5
```

**Entrada e decisões.** `padoIN` lê uma linha do teclado para a variável. `pad` é o *se*, `oin` é o *senão*, e `oin pad` encadeia mais uma condição:

```
PADOIN {
  padoin padoINTER idade👍
  padoPRINT("Sua idade: ")👍
  padoIN(idade)👍

  pad (idade >= 18) {
    padoPRINTLN("Maior de idade")👍
  } oin pad (idade >= 16) {
    padoPRINTLN("Já pode votar")👍
  } oin {
    padoPRINTLN("Menor de idade")👍
  }
}
```

**Laços.** `papapadoin` é o *for*, com as três partes separadas por vírgula; `padoinquanto` é o *while*:

```
PADOIN {
  padoin padoINTER i👍
  papapadoin (i = 1, i <= 3, i++) {
    padoPRINTLN("volta " + i)👍
  }

  padoin padoINTER n = 10👍
  padoinquanto (n > 0) {
    padoPRINT(n + " ")👍
    n = n - 3👍
  }
  padoPRINTLN()👍
}
```

```
volta 1
volta 2
volta 3
10 7 4 1
```

**Funções.** São declaradas antes do `PADOIN` com `padUNCTION`, o tipo de retorno e os parâmetros; `padOUT` devolve o valor:

```
padUNCTION padoINTER dobro(padoINTER x) {
  padOUT x * 2👍
}

padUNCTION padoOLEAN par(padoINTER x) {
  padOUT x padoMOD 2 == 0👍
}

PADOIN {
  padoPRINTLN(dobro(21))👍
  padoPRINTLN(par(7))👍
}
```

```
42
padoFALSE
```

O resto da linguagem (`padBREAK`, `padCONTINUE`, operadores lógicos, conversões, comentários) está na [referência](#referência-da-linguagem).

### 4. Quando algo dá errado

Antes de gerar o C, a padolang confere o programa e aponta a fase, o código da regra violada, a linha e a coluna de cada erro. Com o `check` dá para conferir sem executar:

```
PADOIN {
  padoin padoINTER idade = "vinte"👍
  padoPRINTLN(idad)👍
}
```

```
$ padolang check erro.padoin
padoERRO semântico R4 (linha 2, coluna 28): a variável 'idade' é padoINTER e não pode receber um padoSTRING
padoERRO semântico R1 (linha 3, coluna 15): variável 'idad' não declarada
```

Esquecer o 👍 é um erro sintático, apontado onde o comando deveria ter terminado:

```
padoERRO sintático S1 (linha 3, coluna 1): esperado '👍' ao final do padoPRINTLN, mas encontrado '}'
```

Erros que só aparecem ao rodar, como divisão por zero ou uma letra digitada onde se esperava um número, encerram o programa com `padoERRO de execução (linha N): ...`.

### 5. Use um editor com suporte à padolang

Na página de [Releases](https://github.com/theodoroferreira/padolang/releases) também está o `padolang-0.1.0.vsix`, a extensão para **VS Code** e **Cursor**: cores, erros sublinhados enquanto se digita, formatação com `Shift+Alt+F` e um botão ▷ que executa o arquivo num terminal. Para instalar, "Extensions: Install from VSIX..." no editor, ou:

```bash
code --install-extension padolang-0.1.0.vsix
```

A extensão já traz o transpilador; só precisa do Java 21 e, para executar, do gcc. Detalhes em [`editores/vscode/README.md`](editores/vscode/README.md).

### 6. Próximos passos

A pasta `exemplos/` tem programas prontos para rodar e modificar:

| Exemplo | O que mostra |
|---|---|
| `ola_mundo` | o primeiro programa |
| `tabuada` | entrada com `padoIN` e laço `papapadoin` |
| `par_ou_impar` | o operador de resto `padoMOD` |
| `media_notas` | `padoinquanto`, reais e `oin pad` |
| `saudacao` | textos, concatenação, lógicos e função que devolve `padoSTRING` |
| `fatorial` | função recursiva |
| `fibonacci` | os 15 primeiros números de Fibonacci |
| `primos` | função lógica, `padBREAK` e `padCONTINUE` |

```bash
padolang run exemplos/tabuada.padoin
```

## Referência da linguagem

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
| Comentário | `# até o fim da linha` ou `#* várias linhas *#` | — |

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
- O comentário de bloco vai de `#*` até o primeiro `*#` e não aninha; sem o `*#`, é erro léxico (`L7`).
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

## Linha de comando

```bash
padolang run    arquivo.padoin [--manter]    # gera o C, compila e executa
padolang check  arquivo.padoin [--json]      # confere o programa, sem gerar C
padolang build  arquivo.padoin [-o saida.c]  # gera o C
padolang fmt    arquivo.padoin... [--check | --stdout]  # formata os arquivos
padolang tokens arquivo.padoin [--editor]    # lista os tokens (--editor: comentários e todos os erros léxicos)
padolang ast    arquivo.padoin               # mostra a árvore sintática
padolang lsp                                 # servidor LSP (stdin/stdout), usado pelos editores
padolang --help | --version
```

O `run` usa o compilador C da variável `PADOLANG_CC`, se existir; senão o `gcc`; senão o `cc`. O `.c` e o executável ficam numa pasta temporária, apagada no fim; com `--manter` eles ficam em `saida/`, e com `--dir <pasta>`, na pasta indicada. O programa lê do teclado (ou da entrada redirecionada) e o código de saída é o dele: 1 num `padoERRO de execução`.

O `check --json` imprime os erros com fase, código (`L1`..`L7`, `S1`, `R1`..`R21`), linha, coluna e tamanho, por exemplo `[{"fase":"SEMANTICO","severidade":"ERRO","codigo":"R14","linha":5,"coluna":9,"tamanho":1,"mensagem":"divisão por zero"}]`. Códigos de saída: 0 ok, 1 erro no programa, 2 erro de uso ou de arquivo.

O `fmt` formata no próprio arquivo: 2 espaços por bloco, um comando por linha, `{` na mesma linha, `} oin {`, espaços em volta dos operadores binários, comentários preservados e no máximo uma linha em branco seguida. Com `--stdout` só imprime o resultado; com `--check` só confere e sai com 1 se algum arquivo mudaria. Um arquivo com erro léxico ou sintático não é alterado, e o formatador confere que o resultado tem exatamente os mesmos tokens do original.

O `lsp` é um servidor [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) sem dependências (JSON e JSON-RPC escritos à mão): mostra os erros no editor enquanto se digita (com o código `L`, `S` ou `R` e o trecho sublinhado), dá as cores por Semantic Tokens (parâmetros diferentes de variáveis, por exemplo) e formata o documento com o mesmo formatador do `fmt`. As colunas da padolang contam code points e as do LSP contam unidades UTF-16; a conversão fica em `lsp/Posicoes.java` (um 👍 é 1 coluna na padolang e 2 no editor).

## Desenvolvimento

### Como funciona

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

### Compilar a partir do código

Precisa do **JDK 21** ou superior (no Ubuntu, `sudo apt install openjdk-21-jdk gcc`), do **gcc** e do **bash** (Linux, macOS ou WSL).

```bash
git clone https://github.com/theodoroferreira/padolang.git
cd padolang

./compilar.sh                                  # compila o transpilador em out/ e gera out/padolang.jar
./padolang.sh exemplos/tabuada.padoin          # transpila, compila e executa
```

No repositório, o `bin/padolang` (ou `bin/padolang.cmd`, no Windows) recompila o jar sozinho se algum `.java` mudou. O `padolang.sh` é um atalho para `bin/padolang run --dir saida`, que deixa o C gerado em `saida/<nome>.c`; ele também aceita `--tokens` e `--ast`. Para só gerar o C, sem o jar:

```bash
java -cp out padolang.Main arquivo.padoin -o saida.c
```

A extensão VS Code fica em `editores/vscode/` e é empacotada com o jar dentro:

```bash
./compilar.sh
cd editores/vscode && npm install && npm run empacotar   # gera padolang-0.1.0.vsix
```

### Testes

```bash
./testar.sh        # resultado de cada caso
./testar.sh -v     # também mostra a saída dos programas e as mensagens de erro
```

O script roda todos os programas de `testes/` e `exemplos/`, e o que ele exige depende do nome do arquivo:

- `erro_execucao` no nome: o programa precisa compilar e terminar com `padoERRO de execução`;
- outro `erro` no nome: o transpilador precisa **rejeitar** o programa, com mensagem que começa com `padoERRO`;
- nos dois casos, cada linha `# espera-erro: <trecho>` no arquivo exige que a mensagem contenha esse trecho, o que garante que o erro apontado é o certo (com várias linhas, todos os erros precisam aparecer);
- os demais precisam transpilar, compilar e executar sem falha; se existir `<nome>.saida`, a saída precisa ser exatamente igual a ela;
- se existir `<nome>.entrada`, ele é usado como entrada do programa;
- cada programa tem no máximo 10 segundos, para um laço infinito não travar a suíte.

| Arquivo | Objetivo |
|---|---|
| `01_valido_basico` | declaração, atribuição e saída |
| `02_valido_completo` | condicional, repetição, entrada/saída, função e precedência |
| `03_erro_lexico` | caractere que não pertence à linguagem |
| `04_erro_sintatico` | declaração sem expressão após `=` |
| `05_erro_semantico` | uso sem declaração, declaração duplicada e tipos incompatíveis no mesmo programa: a semântica mostra os três erros |
| `05_erro_semantico_*` | um arquivo por regra semântica (R1 a R21, ver `docs/semantica.md`) |
| `06_valido_semantica` | casos que a semântica precisa aceitar (`padoINTER` → `padoFLOAT`, blocos irmãos, chamada a função declarada depois) |
| `07_erro_execucao_*` | erros que só aparecem ao rodar: divisão por zero, entrada inválida, fim da entrada, conversão fora da faixa |
| `08_valido_expressoes` | lógicos, precedência, curto-circuito, `padoMOD`, `padoINTER(x)`, estouro e escrita de reais |
| `09_valido_textos` | concatenação, comparação de textos, leitura da linha inteira, trigrafias |
| `10_valido_lacos` | `padoinquanto`, `padBREAK`, `padCONTINUE` e chamada como comando |
| `exemplo_funcao` | função com dois parâmetros e condicional |
| `exemplo_textos_e_recursao` | textos, lógicos, recursão e expressões com menos unário |

Os outros scripts conferem as ferramentas: `testar-lexer-editor.sh` (léxico no modo editor), `testar-cli.sh` (subcomandos, `--json`, `run` e códigos de saída), `testar-formatador.sh` (casos de `testes/formatador/` e idempotência) e `testar-lsp.sh` (sessão gravada em `testes/lsp/`).

### Integração contínua e versões

A cada *push* ou *pull request* na `main` ou na `ferramentas`, o GitHub Actions compila o projeto e roda os cinco scripts de teste; depois confere a gramática da extensão VS Code e empacota o `.vsix`. O C gerado e o `.vsix` ficam disponíveis como artefatos da execução.

Para publicar uma versão, atualize `VERSAO` em `src/padolang/cli/Cli.java` e `version` em `editores/vscode/package.json` e envie uma tag com o mesmo número:

```bash
git tag v0.1.0
git push origin v0.1.0
```

O workflow `release.yml` confere se a tag bate com as duas versões, roda os mesmos testes e cria a Release com o `padolang-<versão>.zip` (lançadores `bin/`, `out/padolang.jar` já compilado, exemplos) e o `padolang-<versão>.vsix`.

### Estrutura do repositório

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
├── compilar.sh          compila o transpilador e gera out/padolang.jar
├── padolang.sh          transpila, compila e executa um programa (atalho para bin/padolang run)
├── testar.sh            roda todos os testes e exemplos
├── testar-lexer-editor.sh  confere o léxico no modo editor
├── testar-formatador.sh confere o formatador (testes/formatador/, idempotência, comportamento)
├── testar-lsp.sh        confere o servidor LSP com a sessão gravada em testes/lsp/
└── testar-cli.sh        confere a CLI (subcomandos, --json, run e códigos de saída)
```

## Integrantes

- Andrei Renan de Conto Albrecht - andrei.albrecht@sou.unijui.edu.br
- Alexandre Tonin Mota Rico - alexandre.rico@sou.unijui.edu.br
- Theodoro Gaspar Ferreira - theodoro.ferreira@sou.unijui.edu.br
- Vinícius Andrey Costa Mattos - vinicius.mattos@sou.unijui.edu.br

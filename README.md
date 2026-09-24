# padolang👍

**padolang** é uma linguagem de programação orientada a Edson Luiz Padoin (GOAT), com palavras reservadas construídas a partir de *pad*, *pado* e *padoin* e com o 👍 no lugar do ponto e vírgula. O repositório contém o **transpilador padolang → C**, escrito em Java.

```
PADOIN {
  padoin padoSTRING x = "hello world"👍
  padoPRINT(x)👍
  padoPRINT("\n")👍
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
| Análise semântica | *em desenvolvimento* | tabela de símbolos, escopos e compatibilidade de tipos |
| Geração de código | `src/padolang/gerador/` | percorre a AST e emite C |

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

O `padolang.sh` gera `saida/<nome>.c`, compila com `gcc` e executa o programa. Opções:

```bash
./padolang.sh arquivo.padoin --tokens   # mostra a tabela de tokens
./padolang.sh arquivo.padoin --ast      # mostra a árvore sintática
```

Para só gerar o C, sem compilar nem executar:

```bash
java -cp out padolang.Main arquivo.padoin -o saida.c
```

## A linguagem

Os arquivos têm a extensão `.padoin`. Todo programa começa com `PADOIN { ... }`, e as funções são declaradas antes do bloco principal.

| Construção | padolang | C gerado |
|---|---|---|
| Início do programa | `PADOIN { ... }` | `int main(void) { ... }` |
| Fim de comando | `👍` | `;` |
| Declaração | `padoin padoINTER x = 1👍` | `int pad_x = 1;` |
| Atribuição | `x = x + 1👍` / `x++👍` | `pad_x = pad_x + 1;` / `pad_x++;` |
| Saída | `padoPRINT(x)👍` | `printf("%d", pad_x);` |
| Entrada | `padoIN(x)👍` | `scanf("%d", &pad_x);` |
| Condicional | `pad (x > 1) { } oin { }` | `if (...) { } else { }` |
| Senão-se | `oin pad (x > 0) { }` | `else if (...) { }` |
| Repetição | `papapadoin (i = 0, i < 10, i++) { }` | `for (...; ...; ...) { }` |
| Função | `padUNCTION padoINTER soma(padoINTER a, padoINTER b) { }` | `int pad_soma(int pad_a, int pad_b) { }` |
| Retorno | `padOUT a + b👍` | `return pad_a + pad_b;` |
| Comentário | `# até o fim da linha` | — |

### Tipos

| padolang | C | Literais |
|---|---|---|
| `padoINTER` | `int` | `42` |
| `padoFLOAT` | `double` | `3.14` |
| `padoOLEAN` | `bool` | `padoTRUE`, `padoFALSE` |
| `padoSTRING` | `char *` | `"texto\n"` (escapes `\n`, `\t`, `\"`, `\\`) |

### Operadores

- Aritméticos: `+ - * /`, com a precedência usual e parênteses. Entre inteiros, a divisão é inteira.
- Relacionais: `== != < <= > >=`. Entre textos, a comparação vira `strcmp` no C.
- Menos unário: `-x`.

### Observações

- As palavras reservadas diferenciam maiúsculas de minúsculas: `PADOIN` (programa), `padoin` (declaração) e `padoIN` (entrada) são tokens diferentes.
- Uma variável declarada sem valor começa com o valor nulo do tipo: `0`, `0.0`, `padoNAO` ou `""`.
- `padoPRINT` não pula linha sozinho; use `padoPRINT("\n")👍`.
- No C gerado, todo identificador recebe o prefixo `pad_`. Assim nomes como `int` ou `main` continuam válidos na padolang.
- No `papapadoin`, a inicialização e o passo são opcionais: `papapadoin (, x > 0, ) { }` funciona como um *while*.

A especificação formal (alfabeto Σ, tabela de tokens, gramática em EBNF e regras semânticas) está no relatório técnico.

## Testes

```bash
./testar.sh
```

O script roda todos os programas de `testes/` e `exemplos/`:

- arquivos com `erro` no nome **devem ser rejeitados** pelo transpilador, com mensagem de erro própria;
- os demais devem transpilar, compilar e executar sem falha;
- se existir um `<nome>.entrada` ao lado do `.padoin`, ele é usado como entrada do programa.

| Arquivo | Objetivo |
|---|---|
| `01_valido_basico` | declaração, atribuição e saída |
| `02_valido_completo` | condicional, repetição, entrada/saída, função e precedência |
| `03_erro_lexico` | caractere que não pertence à linguagem |
| `04_erro_sintatico` | declaração sem expressão após `=` |
| `05_erro_semantico` | *a fazer, junto com a análise semântica* |
| `06_valido_textos_e_escopo` | textos, lógicos, recursão e expressões com menos unário |

A cada *push* ou *pull request* na `main`, o GitHub Actions compila o projeto e roda o `testar.sh`. O C gerado fica disponível como artefato da execução.

## Estrutura do repositório

```
padolang/
├── src/padolang/        código do transpilador
│   ├── lexer/
│   ├── parser/
│   ├── ast/
│   ├── gerador/
│   └── Main.java
├── testes/              suíte obrigatória (válidos e inválidos)
├── exemplos/            programas de demonstração
├── compilar.sh          compila o transpilador
├── padolang.sh          transpila, compila e executa um programa
└── testar.sh            roda todos os testes e exemplos
```

## Integrantes

- Andrei Renan de Conto Albrecht - andrei.albrecht@sou.unijui.edu.br
- Alexandre Tonin Mota Rico - alexandre.rico@sou.unijui.edu.br
- Theodoro Gaspar Ferreira - theodoro.ferreira@sou.unijui.edu.br
- Vinícius Andrey Costa Mattos - vinicius.mattos@sou.unijui.edu.br

# Decisões de projeto da padolang

Registro das decisões que fecham o escopo inicial da linguagem. A referência geral é o **Java**: quando uma escolha não é temática, a padolang faz o que o Java faz, e as exceções estão marcadas como "diferente do Java".

## Expressões

**Comparações e lógicos são expressões.** Uma comparação (`<`, `==`...) produz um `padoOLEAN` e pode aparecer em qualquer lugar onde cabe uma expressão: numa variável, num `padOUT`, num argumento. A condição de `pad`, `papapadoin` e `padoinquanto` precisa ser do tipo `padoOLEAN`; `pad (x)` com `x` inteiro é erro semântico, como no Java (e ao contrário do C).

```
padoin padoOLEAN adulto = idade >= 18👍
pad (adulto padAND padNOT bloqueado) { ... }
```

**Operadores lógicos:** `padAND`, `padOR` e `padNOT`. `padAND` e `padOR` são de curto-circuito: `pad (d != 0 padAND x / d > 2)` não divide por zero.

**Resto:** `padoMOD`, só entre `padoINTER`, com o sinal do dividendo (como `%` em Java e C).

**Precedência**, da mais forte para a mais fraca (igual ao Java):

| Nível | Operadores | Associatividade |
|---|---|---|
| 1 | `-` unário, `padNOT` | direita |
| 2 | `*` `/` `padoMOD` | esquerda |
| 3 | `+` `-` | esquerda |
| 4 | `<` `<=` `>` `>=` | esquerda |
| 5 | `==` `!=` | esquerda |
| 6 | `padAND` | esquerda |
| 7 | `padOR` | esquerda |

**Concatenação:** se um dos lados de `+` é `padoSTRING`, o outro vira texto e o resultado é `padoSTRING`, como no Java. Números viram texto no mesmo formato do `padoPRINT`; lógicos, `padoTRUE`/`padoFALSE`. Como `+` é associativo à esquerda, `"a" + 1 + 2` dá `"a12"` e `1 + 2 + "a"` dá `"3a"`.

**Comparação de textos:** `==` e `!=` comparam o **conteúdo**, e `<`, `>` usam a ordem alfabética. *Diferente do Java*, que compara referências com `==`.

**Conversão explícita:** `padoINTER(x)` converte `padoFLOAT` em `padoINTER` truncando em direção a zero (`7.8` → `7`, `-7.8` → `-7`), como `(int)` no Java. Um valor fora da faixa de `padoINTER` é erro de execução (*diferente do Java*, que satura). A conversão implícita de real para inteiro continua proibida (R5); a de inteiro para real continua permitida.

**Divisão por zero literal** (`x / 0`, `x padoMOD 0`) continua sendo erro de compilação (R14). *Diferente do Java*, que só acusa na execução: a padolang pega o erro o mais cedo possível.

## Comandos

| Comando | Sintaxe | Observação |
|---|---|---|
| Laço enquanto | `padoinquanto (cond) { }` | |
| Sair do laço | `padBREAK👍` | só dentro de `papapadoin` ou `padoinquanto` |
| Próxima volta | `padCONTINUE👍` | no `papapadoin`, ainda executa o passo, como no Java |
| Escrever e pular linha | `padoPRINTLN(x)👍` ou `padoPRINTLN()👍` | `padoPRINT` continua sem pular linha |
| Chamada como comando | `f(x)👍` | o valor devolvido é descartado |

**Código inalcançável é erro**, como no Java: nenhum comando pode vir depois de `padOUT`, `padBREAK` ou `padCONTINUE` no mesmo bloco, nem depois de um `pad ... oin` cujos ramos terminam todos assim.

## Tipos e variáveis (mantido)

- Variável declarada sem valor começa com o zero do tipo: `0`, `0.0`, `padoFALSE`, `""`. *Diferente do Java*, que exige atribuição antes do uso.
- Sem sombreamento: não se declara num bloco interno um nome visível de fora (como Java).
- Sem variáveis globais: funções só enxergam parâmetros e variáveis próprias.

## Execução

**Erros de execução** param o programa com uma mensagem no stderr e código de saída 1, no mesmo padrão das outras fases:

```
padoERRO de execução (linha 7): divisão por zero
```

| Situação | Comportamento |
|---|---|
| `/` ou `padoMOD` entre inteiros com divisor 0 | erro de execução (como `ArithmeticException`) |
| divisão de reais por 0 | segue o IEEE 754: `Infinity`, `-Infinity` ou `NaN`, como no Java |
| estouro de inteiro (`2147483647 + 1`) | dá a volta, como no Java (`-2147483648`) |
| `padoINTER(real)` fora da faixa, ou de `NaN` | erro de execução |
| `padoIN` com entrada inválida ou sem entrada | erro de execução (como `InputMismatchException`) |

**Leitura (`padoIN`):**

- `padoINTER` e `padoFLOAT`: um valor por linha. Espaços em volta são ignorados; qualquer outra coisa na linha é entrada inválida.
- `padoOLEAN`: a linha precisa ser `padoTRUE` ou `padoFALSE`, o mesmo que o `padoPRINT` mostra.
- `padoSTRING`: a linha inteira, com espaços, sem limite de tamanho; uma linha vazia vira `""` (como `nextLine()`).

**Escrita de `padoFLOAT`**, igual ao `System.out.println(double)` do Java: o menor número de dígitos que representa o valor, sempre com parte decimal, e notação científica quando o valor absoluto é ≥ 10⁷ ou < 10⁻³.

| Valor | Sai como |
|---|---|
| `7.0` | `7.0` |
| `10.0 / 3` | `3.3333333333333335` |
| `1000000.0` | `1000000.0` |
| `12345678.0` | `1.2345678E7` |
| `0.0001` | `1.0E-4` |

## Comentários

| Comentário | Sintaxe | Observação |
|---|---|---|
| De linha | `# ...` | vai até o fim da linha |
| De bloco | `#* ... *#` | pode ocupar várias linhas ou ficar no meio de uma |

O comentário de bloco **não aninha**: fecha no primeiro `*#`, como o `/* */` do Java e do C. Com aninhamento, os comentários deixariam de ser uma linguagem regular (seria preciso contar aberturas e fechamentos), e o léxico é todo feito de padrões regulares. Um `#*` sem `*#` é erro léxico (`L7`), apontado na posição da abertura. Dentro de um texto (`"#* ... *#"`) e de um comentário de linha, `#*` não abre nada.

## Palavras reservadas novas

`padAND`, `padOR`, `padNOT`, `padoMOD`, `padoPRINTLN`, `padoinquanto`, `padBREAK`, `padCONTINUE`.

## Detalhes de implementação decididos junto

- **Trigrafias:** o C gerado escapa `?` nos textos, para que `"??!"` não vire `|` no gcc.
- **`padolang.sh`:** recompila o transpilador quando algum `.java` muda.
- **Arquivo com BOM** (U+FEFF no início, comum em editores do Windows): é aceito e ignorado.
- **Avisos do gcc** sobre variável ou parâmetro não usado são silenciados: não são erros do programa padolang.
- **`testar.sh`:** cada programa tem limite de tempo, para um laço infinito não travar a suíte nem o CI.
- **Erros semânticos** passam a mostrar linha e coluna, como os léxicos e sintáticos.
- **`5--3`** continua sendo erro sintático: pelo maior casamento, `--` é o decremento (igual ao Java e ao C). Escreve-se `5 - -3`.

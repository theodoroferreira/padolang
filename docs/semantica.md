# Análise semântica da padolang

A análise semântica fica entre o parser e o gerador de código (`src/padolang/semantica/`). O parser garante que o programa tem a **forma** certa; a semântica verifica se ele **faz sentido**: se cada nome existe, se os tipos combinam e se funções e laços são usados como devem. O gerador só roda quando a semântica não encontra nenhum erro.

```
LÉXICO → TOKENS → PARSER → AST → SEMÂNTICA → CÓDIGO C
```

As decisões de linguagem por trás destas regras estão em [`decisoes.md`](decisoes.md).

## Estruturas

| Estrutura | Onde | Conteúdo |
|---|---|---|
| Tabela de funções | `AnalisadorSemantico.funcoes` | nome → assinatura (tipo de retorno e parâmetros) |
| Tabela de símbolos | `TabelaDeSimbolos` | pilha de escopos; cada escopo é um mapa nome → (tipo, linha, é parâmetro?) |
| Anotações | `Anotacoes` | o tipo de cada expressão e de cada `padoIN`, entregue ao gerador |

**Pilha de escopos.** Cada `{ }` empilha um escopo novo, e fechar o bloco desempilha, fazendo as variáveis dele deixarem de existir. A busca por um nome vai do escopo mais interno para o mais externo.

**Funções e PADOIN.** Cada função começa com uma pilha vazia, cujo primeiro escopo guarda os parâmetros (o corpo da função divide esse escopo com eles, como em C e Java). O `PADOIN` também começa com uma pilha própria. Por isso uma função **não enxerga** as variáveis do `PADOIN`: não existem variáveis globais.

**Anotações.** Ao calcular o tipo de cada expressão, a semântica o registra num mapa por identidade de nó. O gerador consulta esse mapa para escolher o `printf`, usar `strcmp` entre textos, gerar a divisão inteira ou a concatenação; assim ele não precisa refazer a inferência de tipos.

## Ordem da análise

1. Percorre as declarações de função e monta a tabela de funções. Como ela fica pronta antes de qualquer corpo ser analisado, uma função pode chamar outra declarada depois dela, ou a si mesma.
2. Analisa o corpo de cada função.
3. Analisa o bloco `PADOIN`.

Uma declaração é analisada na ordem "valor inicial primeiro, depois o nome entra na tabela". Assim, `padoin padoINTER x = x + 1👍` é erro, porque `x` ainda não existe na expressão.

## Regras

| Regra | Descrição | Teste (`05_erro_semantico_*`) |
|---|---|---|
| R1 | Toda variável usada precisa estar declarada e visível no escopo atual. Uma variável declarada dentro de um bloco deixa de existir quando ele fecha, e uma função não enxerga as variáveis do `PADOIN`. | `variavel_nao_declarada`, `fora_do_escopo`, `funcao_ve_padoin` |
| R2 | Não se pode declarar um nome que ainda está visível, no mesmo escopo ou num escopo externo (não há sombreamento). Parâmetros contam como declarados no escopo do corpo da função. Blocos irmãos podem reutilizar o mesmo nome. | `declaracao_duplicada`, `redeclara_parametro` |
| R3 | Uma variável ou parâmetro não pode ter o nome de uma função. | `variavel_com_nome_de_funcao` |
| R4 | Na declaração com valor, na atribuição, na passagem de argumento e no `padOUT`, o valor precisa ser do mesmo tipo do destino, ou `padoINTER` quando o destino é `padoFLOAT`. | `texto_em_inteiro` |
| R5 | `padoFLOAT` não vai implicitamente para `padoINTER`; a conversão precisa ser explícita, com `padoINTER(x)`. | `float_em_inter` |
| R6 | `+ - * /` e o menos unário só se aplicam a números. A exceção é `+` com um `padoSTRING` de um dos lados, que é concatenação. | `operacao_invalida`, `menos_unario_invalido` |
| R7 | `< <= > >=` comparam dois números ou dois textos; `== !=` comparam dois números, dois textos ou dois lógicos. O resultado é `padoOLEAN`. | `comparacao_texto_numero`, `ordem_entre_logicos` |
| R8 | A função chamada precisa existir, receber o número de argumentos que declara, e cada argumento precisa ser compatível (R4) com o parâmetro correspondente. | `funcao_nao_declarada`, `numero_de_argumentos`, `tipo_de_argumento` |
| R9 | Não há duas funções com o mesmo nome (não existe sobrecarga). | `funcao_duplicada` |
| R10 | Um literal inteiro precisa caber em `padoINTER` (0 a 2 147 483 647; o sinal vem do menos unário). Um literal real precisa caber em `padoFLOAT`. | `inteiro_fora_da_faixa` |
| R11 | `padOUT` só pode aparecer dentro de uma função, e o valor precisa ser compatível (R4) com o tipo de retorno dela. | `retorno_fora_de_funcao`, `tipo_do_retorno` |
| R12 | Toda função precisa terminar em `padOUT` em todos os caminhos (ver abaixo). | `funcao_sem_retorno` |
| R13 | `++` e `--` só se aplicam a variáveis `padoINTER` ou `padoFLOAT`. | `incremento_invalido` |
| R14 | Dividir por um zero literal (`x / 0`, `x / 0.0`, `x padoMOD 0`) é erro. Um divisor que só vale zero na execução é pego pelo programa gerado, como erro de execução. | `divisao_por_zero`, `mod_por_zero` |
| R15 | Uma função não pode ser usada como variável (`x = soma👍`), nem uma variável ser chamada como função (`x(2)`). | `funcao_usada_como_variavel`, `variavel_chamada_como_funcao` |
| R16 | A condição de `pad`, `papapadoin` e `padoinquanto` precisa ser `padoOLEAN`. Um número não é condição (como no Java). | `condicao_nao_logica` |
| R17 | `padAND`, `padOR` e `padNOT` só se aplicam a `padoOLEAN`. | `logico_com_numero`, `not_em_numero` |
| R18 | `padoMOD` só se aplica a dois `padoINTER`. | `mod_com_real` |
| R19 | `padoINTER(x)` só converte números (`padoFLOAT` ou `padoINTER`). | `conversao_de_texto` |
| R20 | `padBREAK` e `padCONTINUE` só podem aparecer dentro de `papapadoin` ou `padoinquanto`. | `break_fora_de_laco`, `continue_fora_de_laco` |
| R21 | Nenhum comando pode vir depois de um `padOUT`, `padBREAK` ou `padCONTINUE` no mesmo bloco, nem depois de um `pad ... oin` cujos caminhos todos saem do bloco (código inalcançável, como no Java). | `inalcancavel_apos_retorno`, `inalcancavel_apos_break`, `inalcancavel_apos_pad_oin` |

Os testes `06_valido_semantica`, `08_valido_expressoes`, `09_valido_textos` e `10_valido_lacos` reúnem os casos que a semântica precisa **aceitar**, com a saída esperada em arquivos `.saida`.

### Tipos das expressões

| Expressão | Tipo |
|---|---|
| literal inteiro / real / texto / lógico | `padoINTER` / `padoFLOAT` / `padoSTRING` / `padoOLEAN` |
| variável | o tipo declarado |
| chamada `f(...)` | o tipo de retorno de `f` |
| `padoINTER(x)` | `padoINTER` |
| `-e` | o tipo de `e` (precisa ser numérico) |
| `padNOT e`, `a padAND b`, `a padOR b` | `padoOLEAN` |
| `a op b`, com `op` relacional ou de igualdade | `padoOLEAN` |
| `a + b` com um dos lados `padoSTRING` | `padoSTRING` |
| `a op b`, com `op` em `+ - * /` | `padoINTER` se os dois forem `padoINTER`; senão `padoFLOAT` |
| `a padoMOD b` | `padoINTER` |

A divisão entre dois `padoINTER` é inteira (`7 / 2` vale `3`), como no Java.

### Compatibilidade de atribuição (R4 e R5)

| destino ↓ / valor → | padoINTER | padoFLOAT | padoOLEAN | padoSTRING |
|---|---|---|---|---|
| **padoINTER** | ✓ | ✗ (R5; use `padoINTER(x)`) | ✗ | ✗ |
| **padoFLOAT** | ✓ (alargamento) | ✓ | ✗ | ✗ |
| **padoOLEAN** | ✗ | ✗ | ✓ | ✗ |
| **padoSTRING** | ✗ | ✗ | ✗ | ✓ |

### Operadores relacionais (R7)

| Operandos | `==` `!=` | `<` `<=` `>` `>=` | No C |
|---|---|---|---|
| número e número | ✓ | ✓ | comparação direta |
| texto e texto | ✓ (conteúdo) | ✓ (ordem alfabética) | `strcmp(a, b) op 0` |
| lógico e lógico | ✓ | ✗ | comparação direta |
| tipos diferentes | ✗ | ✗ | — |

### Toda função retorna (R12) e código inalcançável (R21)

As duas regras olham só a estrutura do código, sem avaliar condições.

Para a R12, um bloco **sempre retorna** se algum comando dele sempre retorna: `padOUT` sempre retorna; `pad ... oin ...` sempre retorna se tiver `oin` e os dois ramos sempre retornarem (um `oin pad` encadeado entra na mesma regra); laços nunca contam, porque o corpo pode não executar nenhuma vez.

```
padUNCTION padoINTER sinal(padoINTER n) {
  pad (n > 0) { padOUT 1👍 }
  oin pad (n < 0) { padOUT -1👍 }
}                          # erro: se n == 0, a função termina sem padOUT
```

Para a R21, a ideia é a mesma, trocando "retorna" por "sai do bloco": `padOUT`, `padBREAK` e `padCONTINUE` saem; um `pad ... oin` sai se todos os ramos saírem. Um comando depois disso, no mesmo bloco, nunca executa. É apontado um erro por bloco.

**Simplificação em relação ao Java.** O Java trata `while (true)` como um laço que só termina por `break`, então o código depois dele pode ser inalcançável e uma função que termina nele não precisa de `return`. A padolang não avalia condições, então um laço nunca conta nas duas regras, mesmo com `padoTRUE` na condição.

## Erros

A semântica não para no primeiro erro: ela trabalha sobre uma AST completa e reúne todos os problemas, mostrando um por linha, com o código da regra violada (da tabela acima):

```
padoERRO semântico R1 (linha 5, coluna 10): variável 'total' não declarada
```

A posição é a do token que representa o erro: o nome da variável, o operador de uma operação inválida, a palavra que abre um comando. Quando uma expressão já tem erro, o tipo dela vira "desconhecido" e as verificações que dependem dela são puladas, o que evita uma cascata de mensagens a partir de um único engano.

## Relação com o gerador e com a execução

O gerador parte do princípio de que o programa está correto: as exceções que sobraram nele (`IllegalStateException`) indicam defeito do próprio transpilador, nunca erro do programa padolang.

O que só pode ser descoberto ao rodar (divisão inteira por uma variável que vale zero, entrada inválida no `padoIN`, `padoINTER(x)` fora da faixa) é conferido pelo programa C gerado, que termina com `padoERRO de execução (linha N): ...` e código de saída 1. Os testes `07_erro_execucao_*` cobrem esses casos.

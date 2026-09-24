# Análise semântica da padolang

A análise semântica fica entre o parser e o gerador de código (`src/padolang/semantica/`). O parser garante que o programa tem a **forma** certa; a semântica verifica se ele **faz sentido**: se cada nome existe, se os tipos combinam e se as funções são usadas como foram declaradas. O gerador só roda quando a semântica não encontra nenhum erro.

```
LÉXICO → TOKENS → PARSER → AST → SEMÂNTICA → CÓDIGO C
```

## Estruturas

| Estrutura | Onde | Conteúdo |
|---|---|---|
| Tabela de funções | `AnalisadorSemantico.funcoes` | nome → assinatura (tipo de retorno e parâmetros) |
| Tabela de símbolos | `TabelaDeSimbolos` | pilha de escopos; cada escopo é um mapa nome → (tipo, linha, é parâmetro?) |

**Pilha de escopos.** Cada `{ }` empilha um escopo novo, e fechar o bloco desempilha, fazendo as variáveis dele deixarem de existir. A busca por um nome vai do escopo mais interno para o mais externo.

**Funções e PADOIN.** Cada função começa com uma pilha vazia, cujo primeiro escopo guarda os parâmetros (o corpo da função divide esse escopo com eles, como em C e Java). O `PADOIN` também começa com uma pilha própria. Por isso uma função **não enxerga** as variáveis do `PADOIN`: não existem variáveis globais.

## Ordem da análise

1. Percorre as declarações de função e monta a tabela de funções. Como ela fica pronta antes de qualquer corpo ser analisado, uma função pode chamar outra declarada depois dela, ou a si mesma.
2. Analisa o corpo de cada função.
3. Analisa o bloco `PADOIN`.

Uma declaração é analisada na ordem "valor inicial primeiro, depois o nome entra na tabela". Assim, `padoin padoINTER x = x + 1👍` é erro, porque `x` ainda não existe na expressão.

## Regras

| Regra | Descrição | Teste |
|---|---|---|
| R1 | Toda variável usada precisa estar declarada e visível no escopo atual. Uma variável declarada dentro de um bloco deixa de existir quando ele fecha, e uma função não enxerga as variáveis do `PADOIN`. | `05_erro_semantico_variavel_nao_declarada`, `_fora_do_escopo`, `_funcao_ve_padoin` |
| R2 | Não se pode declarar um nome que ainda está visível, seja no mesmo escopo ou em um escopo externo (não há sombreamento). Parâmetros contam como declarados no escopo do corpo da função. Blocos irmãos podem reutilizar o mesmo nome. | `_declaracao_duplicada`, `_redeclara_parametro` |
| R3 | Uma variável ou parâmetro não pode ter o nome de uma função. | `_variavel_com_nome_de_funcao` |
| R4 | Na declaração com valor, na atribuição, na passagem de argumento e no `padOUT`, o valor precisa ser do mesmo tipo do destino, ou `padoINTER` quando o destino é `padoFLOAT`. | `_texto_em_inteiro` |
| R5 | `padoFLOAT` não pode ir para `padoINTER`, porque a parte decimal seria perdida sem aviso. | `_float_em_inter` |
| R6 | `+ - * /` e o menos unário só se aplicam a `padoINTER` e `padoFLOAT`. | `_operacao_invalida`, `_menos_unario_invalido` |
| R7 | Uma comparação precisa de dois números, dois textos ou dois lógicos. Lógicos só aceitam `==` e `!=`. | `_comparacao_texto_numero`, `_ordem_entre_logicos` |
| R8 | A função chamada precisa existir, receber o número de argumentos que declara e cada argumento precisa ser compatível (R4) com o parâmetro correspondente. | `_funcao_nao_declarada`, `_numero_de_argumentos`, `_tipo_de_argumento` |
| R9 | Não há duas funções com o mesmo nome (não existe sobrecarga). | `_funcao_duplicada` |
| R10 | Um literal inteiro precisa caber em `padoINTER` (0 a 2 147 483 647; o sinal vem do menos unário). Um literal real precisa caber em `padoFLOAT`. | `_inteiro_fora_da_faixa` |
| R11 | `padOUT` só pode aparecer dentro de uma função, e o valor precisa ser compatível (R4) com o tipo de retorno dela. | `_retorno_fora_de_funcao`, `_tipo_do_retorno` |
| R12 | Toda função precisa terminar em `padOUT` em todos os caminhos (ver abaixo). | `_funcao_sem_retorno` |
| R13 | `++` e `--` só se aplicam a variáveis `padoINTER` ou `padoFLOAT`. | `_incremento_invalido` |
| R14 | Dividir por um zero literal (`x / 0`, `x / 0.0`) é erro. | `_divisao_por_zero` |
| R15 | Uma função não pode ser usada como variável (`x = soma👍`), nem uma variável ser chamada como função (`x(2)`). | `_funcao_usada_como_variavel`, `_variavel_chamada_como_funcao` |

O teste `06_valido_semantica` reúne os casos que a semântica precisa **aceitar**: chamada a uma função declarada depois, `padoINTER` → `padoFLOAT` em atribuição, argumento e retorno, o mesmo nome em blocos irmãos, comparação entre textos e laço com passo em expressão.

### Tipos das expressões

| Expressão | Tipo |
|---|---|
| literal inteiro / real / texto / lógico | `padoINTER` / `padoFLOAT` / `padoSTRING` / `padoOLEAN` |
| variável | o tipo declarado |
| chamada `f(...)` | o tipo de retorno de `f` |
| `-e` | o tipo de `e` (precisa ser numérico) |
| `a op b`, com `op` em `+ - * /` | `padoINTER` se os dois forem `padoINTER`; senão `padoFLOAT` |

A divisão entre dois `padoINTER` é inteira (`7 / 2` vale `3`), como no C.

### Compatibilidade de atribuição (R4 e R5)

| destino ↓ / valor → | padoINTER | padoFLOAT | padoOLEAN | padoSTRING |
|---|---|---|---|---|
| **padoINTER** | ✓ | ✗ (R5) | ✗ | ✗ |
| **padoFLOAT** | ✓ (alargamento) | ✓ | ✗ | ✗ |
| **padoOLEAN** | ✗ | ✗ | ✓ | ✗ |
| **padoSTRING** | ✗ | ✗ | ✗ | ✓ |

### Operadores relacionais (R7)

| Operandos | `==` `!=` | `<` `<=` `>` `>=` | No C |
|---|---|---|---|
| número e número | ✓ | ✓ | comparação direta |
| texto e texto | ✓ | ✓ (ordem alfabética) | `strcmp(a, b) op 0` |
| lógico e lógico | ✓ | ✗ | comparação direta |
| tipos diferentes | ✗ | ✗ | — |

### Toda função retorna (R12)

Como a padolang não tem funções sem retorno, a semântica verifica se todo caminho termina em `padOUT`. A regra olha só a estrutura do código, sem avaliar condições:

- um bloco sempre retorna se algum comando dele sempre retorna;
- `padOUT` sempre retorna;
- `pad ... oin ...` sempre retorna se tiver `oin` e **os dois** ramos sempre retornarem (um `oin pad` encadeado entra na mesma regra);
- `papapadoin` nunca conta, porque o corpo pode não executar nenhuma vez.

```
padUNCTION padoINTER sinal(padoINTER n) {
  pad (n > 0) { padOUT 1👍 }
  oin pad (n < 0) { padOUT -1👍 }
}                          # erro: se n == 0, a função termina sem padOUT
```

## Decisões de projeto

- **`padoFLOAT` → `padoINTER` é erro (R5).** Em C a conversão é implícita e trunca em silêncio, então `padoin padoINTER n = 7.5👍` guardaria 7. Proibir torna a perda de informação visível; o caminho contrário, `padoINTER` → `padoFLOAT`, não perde nada e é permitido.
- **Sem sombreamento (R2).** Como em Java, não se pode declarar dentro de um bloco um nome que já existe fora dele. Isso evita o erro clássico de achar que se está alterando a variável de fora. Blocos irmãos continuam podendo repetir nomes.
- **Sem variáveis globais.** Funções só enxergam os próprios parâmetros e variáveis; dados do `PADOIN` entram por parâmetro.
- **Todos os erros de uma vez.** O léxico e o sintático param no primeiro erro, porque depois dele os tokens seguintes perdem o sentido. A semântica trabalha sobre uma AST completa e pode continuar, então reúne todos os erros e mostra um por linha, na forma `Erro semântico (linha N): ...`. Quando uma expressão já tem erro, o tipo dela vira "desconhecido" e as verificações que dependem dela são puladas, o que evita uma cascata de mensagens a partir de um único engano.

## Relação com o gerador

Antes da semântica, o `GeradorC` tinha checagens provisórias de variável e função não declaradas e de operação inválida, que lançavam `GeracaoException`. Elas saíram: o gerador agora parte do princípio de que o programa está correto, e as exceções que sobraram (`IllegalStateException`) indicam defeito do próprio transpilador, nunca erro do programa padolang.

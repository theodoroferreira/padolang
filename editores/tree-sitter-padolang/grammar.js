/**
 * Gramática Tree-sitter mínima da padolang.
 *
 * Não reproduz a GLC do compilador (isso é trabalho do parser em Java). Ela existe porque o Zed só
 * registra uma linguagem que tenha uma gramática Tree-sitter, e serve para as cores básicas, os
 * pares de chaves, a indentação e o outline. Por isso é quase "plana": uma sequência de itens
 * (palavras reservadas, tipos, literais, identificadores, operadores), com estrutura só onde ela
 * ajuda o editor:
 *   - bloco `{ ... }` e parenteses `( ... )`, para casar os pares e indentar;
 *   - programa `PADOIN { ... }` e definicao_funcao `padUNCTION <tipo> nome (...) { ... }`, para o outline;
 *   - declaracao `padoin <tipo> nome`, para marcar o nome declarado;
 *   - chamada `nome(...)`, para colorir o nome como função.
 *
 * Os léxicos seguem o Lexer.java: identificadores só com letras ASCII, `#` até o fim da linha, `#* ... *#`,
 * textos com os escapes \n \t \" \\ e o 👍 com tom de pele e seletor de variação opcionais.
 */

module.exports = grammar({
  name: 'padolang',

  // O BOM (U+FEFF) é ignorado pelo Lexer.java; aqui também
  extras: $ => [/[ \t\r\n\uFEFF]/, $.comment],

  // Palavras reservadas são reconhecidas como identificadores e depois separadas (padoin ≠ padoinquanto)
  word: $ => $.identificador,

  rules: {
    source_file: $ => repeat($._item),

    _item: $ => choice(
      $.programa,
      $.definicao_funcao,
      $.declaracao,
      $.chamada,
      $.bloco,
      $.parenteses,
      $.palavra_reservada,
      $.tipo,
      $.booleano,
      $.texto,
      $.numero,
      $.identificador,
      $.operador,
      ',',
      $.joinha,
    ),

    programa: $ => seq('PADOIN', field('corpo', $.bloco)),

    definicao_funcao: $ => seq(
      'padUNCTION',
      field('tipo', $.tipo),
      field('nome', $.identificador),
      field('parametros', $.parenteses),
      field('corpo', $.bloco),
    ),

    declaracao: $ => seq('padoin', field('tipo', $.tipo), field('nome', $.identificador)),

    // prec(1): diante de `nome (`, prefere a chamada a um identificador solto seguido de parênteses
    chamada: $ => prec(1, seq(field('nome', $.identificador), field('argumentos', $.parenteses))),

    bloco: $ => seq('{', repeat($._item), '}'),

    parenteses: $ => seq('(', repeat($._item), ')'),

    palavra_reservada: _ => choice(
      'pad', 'oin', 'papapadoin', 'padoinquanto', 'padBREAK', 'padCONTINUE', 'padOUT',
      'padoIN', 'padoPRINT', 'padoPRINTLN',
      'padAND', 'padOR', 'padNOT', 'padoMOD',
    ),

    tipo: _ => choice('padoINTER', 'padoFLOAT', 'padoOLEAN', 'padoSTRING'),

    booleano: _ => choice('padoTRUE', 'padoFALSE'),

    operador: _ => choice(
      '++', '--', '+', '-', '*', '/',
      '==', '!=', '<=', '>=', '<', '>', '=',
    ),

    texto: $ => seq(
      '"',
      repeat(choice($._conteudo_texto, $.escape)),
      token.immediate('"'),
    ),

    // prec(1): dentro do texto, `#` é conteúdo e não começo de comentário
    _conteudo_texto: _ => token.immediate(prec(1, /[^"\\\n]+/)),

    escape: _ => token.immediate(/\\[nt"\\]/),

    numero: _ => /[0-9]+(\.[0-9]+)?/,

    identificador: _ => /[A-Za-z_][A-Za-z0-9_]*/,

    // 👍, 👍🏻 a 👍🏿 e 👍️ (com o seletor de variação U+FE0F)
    joinha: _ => /\u{1F44D}[\u{1F3FB}-\u{1F3FF}]?\u{FE0F}?/u,

    // O nome em inglês é de propósito: o `tree-sitter test` só lê as asserções dos testes de cores
    // (test/highlight/) em nós cujo nome contém "comment".
    // De linha: `#` até o fim da linha, desde que não comece por `#*`; de bloco: de `#*` até o
    // primeiro `*#`. O de linha exclui o `*` porque o Tree-sitter fica com o maior casamento, e
    // `#* a *# x` casaria inteiro como comentário de linha.
    comment: _ => token(choice(
      seq('#', optional(seq(/[^*\n]/, /[^\n]*/))),
      seq('#*', /([^*]|\*+[^*#])*\*+/, '#'),
    )),
  },
});

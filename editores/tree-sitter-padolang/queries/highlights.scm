; Cores da padolang. Cópia idêntica em editores/zed/languages/padolang/highlights.scm
; (o testar-zed.sh confere). Nenhum nó recebe duas capturas, porque o tree-sitter CLI
; fica com a primeira e o Zed com a última: por isso não há um "(identificador) @variable" geral.

(comment) @comment

(texto) @string
(escape) @string.escape

(numero) @number
(booleano) @boolean

(tipo) @type

[
  "PADOIN"
  "padoin"
  "padUNCTION"
  "pad"
  "oin"
  "papapadoin"
  "padoinquanto"
  "padBREAK"
  "padCONTINUE"
  "padOUT"
] @keyword

[
  "padoIN"
  "padoPRINT"
  "padoPRINTLN"
] @function.builtin

[
  "padAND"
  "padOR"
  "padNOT"
  "padoMOD"
] @keyword.operator

(operador) @operator

(definicao_funcao nome: (identificador) @function)
(chamada nome: (identificador) @function)

(definicao_funcao
  parametros: (parenteses (identificador) @variable.parameter))

(declaracao nome: (identificador) @variable)

[
  "("
  ")"
  "{"
  "}"
] @punctuation.bracket

"," @punctuation.delimiter

(joinha) @punctuation.delimiter

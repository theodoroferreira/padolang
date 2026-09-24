// Confere a gramática TextMate com o mesmo motor do VS Code (vscode-textmate + vscode-oniguruma).
// Cada caso: uma linha de padolang, um trecho dela e o escopo mais interno esperado para esse trecho.
// Sai com código 1 se algum caso falhar.
const fs = require("fs");
const path = require("path");
const oniguruma = require("vscode-oniguruma");
const textmate = require("vscode-textmate");

const casos = [
  ["padUNCTION padoINTER soma(padoINTER a, padoINTER b) {", "padUNCTION", "storage.type.function.padolang"],
  ["padUNCTION padoINTER soma(padoINTER a, padoINTER b) {", "padoINTER", "storage.type.padolang"],
  ["padUNCTION padoINTER soma(padoINTER a, padoINTER b) {", "soma", "entity.name.function.padolang"],
  ["padUNCTION padoINTER soma(padoINTER a, padoINTER b) {", "a", "variable.other.padolang"],
  ["PADOIN {", "PADOIN", "keyword.other.padolang"],
  ["  padoin padoFLOAT media = 7.5👍", "padoin", "keyword.other.padolang"],
  ["  padoin padoFLOAT media = 7.5👍", "padoFLOAT", "storage.type.padolang"],
  ["  padoin padoFLOAT media = 7.5👍", "media", "variable.other.padolang"],
  ["  padoin padoFLOAT media = 7.5👍", "=", "keyword.operator.assignment.padolang"],
  ["  padoin padoFLOAT media = 7.5👍", "7.5", "constant.numeric.float.padolang"],
  ["  padoin padoFLOAT media = 7.5👍", "👍", "punctuation.terminator.statement.padolang"],
  ["  x = 10👍🏽", "10", "constant.numeric.integer.padolang"],
  ["  x = 10👍🏽", "👍🏽", "punctuation.terminator.statement.padolang"],
  ["  x = 10👍️", "👍️", "punctuation.terminator.statement.padolang"],
  ["  x1 = x1 + 1👍", "x1", "variable.other.padolang"],
  ["  padoPRINTLN(\"a\\tb\\n\" + nome)👍", "padoPRINTLN", "support.function.builtin.padolang"],
  ["  padoPRINTLN(\"a\\tb\\n\" + nome)👍", "\\t", "constant.character.escape.padolang"],
  ["  padoPRINTLN(\"a\\tb\\n\" + nome)👍", "+", "keyword.operator.arithmetic.padolang"],
  ["  padoPRINT(\"ruim \\q\")👍", "\\q", "invalid.illegal.escape.padolang"],
  ["  padoPRINT(\"sem fim)👍", "👍", "string.quoted.double.padolang"],
  ["  padoIN(n)👍", "padoIN", "support.function.builtin.padolang"],
  ["  pad (a >= 0 padAND padNOT b) {", "pad", "keyword.control.padolang"],
  ["  pad (a >= 0 padAND padNOT b) {", ">=", "keyword.operator.comparison.padolang"],
  ["  pad (a >= 0 padAND padNOT b) {", "padAND", "keyword.operator.word.padolang"],
  ["  pad (a >= 0 padAND padNOT b) {", "padNOT", "keyword.operator.word.padolang"],
  ["  } oin {", "oin", "keyword.control.padolang"],
  ["  papapadoin (i = 0, i < 10, i++) {", "papapadoin", "keyword.control.padolang"],
  ["  papapadoin (i = 0, i < 10, i++) {", "++", "keyword.operator.increment.padolang"],
  ["  papapadoin (i = 0, i < 10, i++) {", ",", "punctuation.separator.comma.padolang"],
  ["  padoinquanto (padoTRUE) { padBREAK👍 }", "padoinquanto", "keyword.control.padolang"],
  ["  padoinquanto (padoTRUE) { padBREAK👍 }", "padoTRUE", "constant.language.boolean.padolang"],
  ["  padoinquanto (padoTRUE) { padBREAK👍 }", "padBREAK", "keyword.control.padolang"],
  ["  padOUT n padoMOD 2 == 0👍", "padOUT", "keyword.control.padolang"],
  ["  padOUT n padoMOD 2 == 0👍", "padoMOD", "keyword.operator.word.padolang"],
  ["  padoin padoINTER x = padoINTER(3.7)👍", "padoINTER(", "storage.type.padolang"],
  ["  padoin padoINTER r = soma(1, 2)👍", "soma", "entity.name.function.padolang"],
  ["  padoin padoINTER r = soma(1, 2)👍", "r", "variable.other.padolang"],
  ["  x = 1👍 # padoin \"nada\" 👍", "padoin", "comment.line.number-sign.padolang"],
  ["  x = 1👍 # padoin \"nada\" 👍", "#", "punctuation.definition.comment.padolang"],
  ["padoPRINTLN(\"# não é comentário\")👍", "#", "string.quoted.double.padolang"],
];

/** Primeira ocorrência do trecho; se ele é uma palavra, só como palavra inteira (o "a" de "padUNCTION" não conta). */
function procurar(linha, trecho) {
  const palavra = /\w/;
  for (let i = linha.indexOf(trecho); i >= 0; i = linha.indexOf(trecho, i + 1)) {
    const antes = linha[i - 1] ?? " ";
    const depois = linha[i + trecho.length] ?? " ";
    if (!/^\w+$/.test(trecho) || (!palavra.test(antes) && !palavra.test(depois))) {
      return i;
    }
  }
  return -1;
}

async function main() {
  const wasm = fs.readFileSync(require.resolve("vscode-oniguruma/release/onig.wasm")).buffer;
  await oniguruma.loadWASM(wasm);
  const registro = new textmate.Registry({
    onigLib: Promise.resolve({
      createOnigScanner: (fontes) => new oniguruma.OnigScanner(fontes),
      createOnigString: (s) => new oniguruma.OnigString(s),
    }),
    loadGrammar: async () => {
      const arquivo = path.join(__dirname, "..", "syntaxes", "padolang.tmLanguage.json");
      return textmate.parseRawGrammar(fs.readFileSync(arquivo, "utf8"), arquivo);
    },
  });
  const gramatica = await registro.loadGrammar("source.padolang");

  let falhas = 0;
  for (const [linha, trecho, esperado] of casos) {
    // "padoINTER(" confere só o "padoINTER"; o resto confere o trecho inteiro
    const alvo = trecho.endsWith("(") && trecho.length > 1 ? trecho.slice(0, -1) : trecho;
    const inicio = procurar(linha, trecho);
    const { tokens } = gramatica.tokenizeLine(linha, textmate.INITIAL);
    const token = tokens.find((t) => t.startIndex <= inicio && inicio < t.endIndex);
    const obtido = token ? token.scopes[token.scopes.length - 1] : "(nenhum)";
    const inteiro = token && token.startIndex === inicio && token.endIndex >= inicio + alvo.length;
    const pedaco = token ? linha.slice(token.startIndex, token.endIndex) : "";
    const ok = obtido === esperado && (inteiro || esperado.startsWith("string") || esperado.startsWith("comment"));
    if (ok) {
      console.log(`[ OK ]  ${JSON.stringify(alvo)} em ${JSON.stringify(linha.trim())}: ${esperado}`);
    } else {
      falhas++;
      console.log(`[FALHA] ${JSON.stringify(alvo)} em ${JSON.stringify(linha.trim())}: esperado ${esperado}, ` +
        `obtido ${obtido} no trecho ${JSON.stringify(pedaco)}`);
    }
  }
  console.log(`\n${casos.length - falhas} de ${casos.length} casos passaram.`);
  process.exit(falhas === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

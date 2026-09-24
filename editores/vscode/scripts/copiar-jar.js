// Copia o out/padolang.jar do repositório para server/ e o LICENSE para a raiz da extensão,
// antes do empacotamento (vsce package roda isto pelo "vscode:prepublish").
// O jar é gerado pelo ./compilar.sh na raiz do repositório.
const fs = require("fs");
const path = require("path");

const raiz = path.resolve(__dirname, "..", "..", "..");
const extensao = path.resolve(__dirname, "..");
const jar = path.join(raiz, "out", "padolang.jar");

if (!fs.existsSync(jar)) {
  console.error(`${jar} não existe. Rode ./compilar.sh na raiz do repositório antes de empacotar.`);
  process.exit(1);
}

fs.mkdirSync(path.join(extensao, "server"), { recursive: true });
fs.copyFileSync(jar, path.join(extensao, "server", "padolang.jar"));
fs.copyFileSync(path.join(raiz, "LICENSE"), path.join(extensao, "LICENSE"));
console.log(`copiado: ${path.relative(raiz, jar)} -> editores/vscode/server/padolang.jar`);

# Padolang para VS Code e Cursor

Suporte aos arquivos `.padoin` da [padolang👍](https://github.com/theodoroferreira/padolang):

- **cores**: a gramática TextMate colore palavras reservadas, tipos, números, textos, comentários e o `👍` assim que o arquivo abre, mesmo sem Java. Quando o servidor sobe, as cores semânticas completam a gramática: parâmetros aparecem diferentes das variáveis e as funções são reconhecidas;
- **erros enquanto se digita**: léxicos (`L1`..`L7`), sintáticos (`S1`) e semânticos (`R1`..`R21`), sublinhados exatamente no trecho do erro;
- **formatação**: `Shift+Alt+F` (ou "Format Document") usa o mesmo formatador do `padolang fmt`. Para formatar ao salvar, acrescente em `settings.json`:
  ```json
  "[padolang]": { "editor.formatOnSave": true }
  ```
- **executar**: "Padolang: Executar arquivo" (paleta de comandos ou o botão ▷ no canto do editor) salva o arquivo e roda `padolang run` como uma task, num terminal integrado: o `padoIN` lê do teclado e a saída continua na tela depois que o programa termina. Rodar de novo encerra a execução anterior.

## Requisitos

- **Java 21** ou mais novo. Se o `java` não estiver no `PATH`, indique o caminho em `padolang.java`.
- **gcc** (ou outro compilador C, pela variável `PADOLANG_CC`) só para executar os programas.

## Configurações

| Configuração | Padrão | Para quê |
|---|---|---|
| `padolang.java` | `"java"` | comando do Java |
| `padolang.jar` | `""` | caminho do `padolang.jar`; vazio usa o que vem na extensão. Aceita `${workspaceFolder}`, por exemplo `"${workspaceFolder}/out/padolang.jar"` para testar o transpilador do repositório sem reempacotar |

Mudar uma delas reinicia o servidor. "Padolang: Reiniciar o servidor" também reinicia, e o log dele fica no painel Output, em "Padolang LSP".

## Gerar e instalar o `.vsix`

Na raiz do repositório:

```bash
./compilar.sh                  # gera out/padolang.jar
cd editores/vscode
npm install
npm run empacotar              # compila, copia o jar para server/ e gera padolang-0.1.0.vsix
```

- **VS Code:** "Extensions: Install from VSIX..." e escolher o `padolang-0.1.0.vsix`, ou `code --install-extension padolang-0.1.0.vsix`.
- **Cursor:** o mesmo `.vsix`, pelo mesmo comando ("Extensions: Install from VSIX...") ou `cursor --install-extension padolang-0.1.0.vsix`.
- **Outros forks do VS Code** (Antigravity, Windsurf, VSCodium): o mesmo `.vsix`, pelo "Install from VSIX..." ou pelo comando do próprio editor (ex.: `antigravity-ide --install-extension`). O `code --install-extension` instala só no VS Code.

## Desenvolvimento

- `npm run compile` confere os tipos (`tsc`) e gera `out/extension.js` num único arquivo (`esbuild`).
- `npm test` confere a gramática TextMate com o mesmo motor do VS Code (`vscode-textmate` + `vscode-oniguruma`).
- Para depurar, abra a pasta `editores/vscode` no VS Code e aperte `F5`: abre uma janela nova com a extensão carregada. Rode `npm run copiar-jar` antes, ou aponte `padolang.jar` para o `out/padolang.jar` do repositório.

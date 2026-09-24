# Extensão Zed da padolang👍

Registra a linguagem **Padolang** (arquivos `.padoin`) no [Zed](https://zed.dev) e inicia o servidor LSP da padolang (`padolang lsp`), o mesmo da extensão do VS Code:

- cores por Semantic Tokens (parâmetros, funções e declarações diferentes de variáveis), com as cores da gramática Tree-sitter como alternativa;
- erros léxicos, sintáticos e semânticos sublinhados enquanto se digita, com o código (`L1`, `S1`, `R14`...);
- formatação (a mesma do `padolang fmt`), ao salvar ou por "editor: format";
- pares de `{}`, `()` e `""`, indentação por bloco, comentário com `#` ("editor: toggle comments") e outline com o `PADOIN` e as funções.

## Requisitos

- **Java 21** ou mais novo;
- **`padolang` no `PATH`**: a pasta `bin/` do repositório (veja o README principal). Com o próprio repositório da padolang aberto no Zed, o `bin/padolang` dele é usado mesmo fora do `PATH`;
- **Rust instalado pelo [rustup](https://rustup.rs)**: o Zed compila a extensão (`src/lib.rs`) para WebAssembly e instala sozinho o alvo `wasm32-wasip2`.

## Instalar

1. No Zed, abra a paleta de comandos e rode **"zed: install dev extension"**.
2. Escolha a pasta `editores/zed/` deste repositório.
3. Abra um `.padoin`: o seletor de linguagem, no canto inferior direito, mostra "Padolang".

O log da instalação e do servidor fica em "zed: open log". Se o servidor não subir, confira `padolang --version` no terminal.

### Repositório privado

O Zed baixa a gramática com `git`, a partir do `repository` e do `rev` em `extension.toml`. Enquanto o repositório for privado, o `git` precisa conseguir clonar `https://github.com/theodoroferreira/padolang` (por exemplo, com `gh auth setup-git`). Sem isso, para desenvolver, troque **localmente** o `repository` por `file:///caminho/completo/do/padolang` e reinstale a extensão. Não commite essa troca.

## Configurações

O `.zed/settings.json` do repositório já traz o necessário. Para usar em outros projetos, copie para as configurações do Zed ("zed: open settings"):

```json
{
  "languages": {
    "Padolang": {
      "semantic_tokens": "full",
      "tab_size": 2,
      "formatter": "language_server",
      "format_on_save": "on"
    }
  }
}
```

- `"semantic_tokens": "full"`: as cores vêm só do LSP. O padrão do Zed é `"off"` (só Tree-sitter), e `"combined"` junta as duas.
- Para usar outro lançador ou o jar direto:

  ```json
  {
    "lsp": {
      "padolang-lsp": {
        "binary": { "path": "java", "arguments": ["-jar", "/caminho/do/padolang/out/padolang.jar", "lsp"] }
      }
    }
  }
  ```

## Tarefas

O `.zed/tasks.json` do repositório define "padolang: executar", "conferir", "gerar C", "formatar" e "testar". Abra com "task: spawn" ou pelo botão ▶ ao lado do `PADOIN`. O programa roda no terminal do Zed, então o `padoIN` lê do teclado.

## Estrutura

```
editores/zed/
  extension.toml            id, versão, gramática (repository + rev + path) e o servidor padolang-lsp
  Cargo.toml, src/lib.rs    acha o padolang (configuração, PATH ou bin/ do projeto) e roda "padolang lsp"
  languages/padolang/
    config.toml             nome, extensão .padoin, comentário "# ", pares, tab de 2 espaços
    highlights.scm          cores da gramática (cópia de editores/tree-sitter-padolang/queries/)
    brackets.scm, indents.scm, outline.scm, overrides.scm, runnables.scm
```

A gramática Tree-sitter fica em [`../tree-sitter-padolang/`](../tree-sitter-padolang/). Ela é propositalmente quase "plana": não reproduz a GLC do compilador, só dá ao Zed os blocos, os parênteses, as funções e os tokens para colorir.

## Quando mudar a gramática

1. Edite `editores/tree-sitter-padolang/grammar.js` e rode `npm run gerar` nessa pasta. O `src/` gerado vai para o git.
2. Commite a gramática e envie o commit ao GitHub.
3. Atualize o `rev` do `extension.toml` para esse commit e, no Zed, rode "zed: rebuild dev extension".

O `./testar-zed.sh` confere tudo isso: o `src/` atualizado, os testes da gramática (`test/corpus/` e `test/highlight/`), os programas do repositório lidos sem erro, as queries, o `rev` e a compilação da extensão.

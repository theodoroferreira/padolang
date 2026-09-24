//! Extensão do Zed para a padolang: registra a linguagem (gramática Tree-sitter e queries em
//! `languages/padolang/`) e diz ao Zed como iniciar o servidor LSP (`padolang lsp`).
//!
//! O servidor é o mesmo da extensão do VS Code, escrito em Java. Esta extensão só o encontra:
//!   1. `lsp.padolang-lsp.binary.path` nas configurações do Zed, se existir;
//!   2. `padolang` no PATH (o `bin/` do repositório, ou um link simbólico para ele);
//!   3. `bin/padolang` na raiz do projeto aberto, quando o projeto é o próprio repositório da padolang.

use zed_extension_api::{self as zed, settings::LspSettings, LanguageServerId, Result};

struct Padolang;

impl Padolang {
    /// Caminho do lançador dentro do repositório da padolang, se o projeto aberto for ele.
    fn lancador_do_repositorio(worktree: &zed::Worktree) -> Option<String> {
        // read_text_file só lê dentro do projeto: serve para saber se o lançador está lá
        worktree.read_text_file("bin/padolang").ok()?;
        let raiz = worktree.root_path();
        Some(match zed::current_platform().0 {
            zed::Os::Windows => format!("{raiz}\\bin\\padolang.cmd"),
            _ => format!("{raiz}/bin/padolang"),
        })
    }
}

impl zed::Extension for Padolang {
    fn new() -> Self {
        Padolang
    }

    fn language_server_command(
        &mut self,
        language_server_id: &LanguageServerId,
        worktree: &zed::Worktree,
    ) -> Result<zed::Command> {
        // O ambiente do shell do usuário leva junto o PATH e o JAVA_HOME que o lançador usa para achar o java
        let mut env = worktree.shell_env();

        let binario = LspSettings::for_worktree(language_server_id.as_ref(), worktree)
            .ok()
            .and_then(|configuracao| configuracao.binary);

        if let Some(binario) = binario {
            if let Some(extra) = binario.env {
                env.extend(extra);
            }
            if let Some(caminho) = binario.path {
                return Ok(zed::Command {
                    command: caminho,
                    args: binario.arguments.unwrap_or_else(|| vec!["lsp".into()]),
                    env,
                });
            }
        }

        let comando = worktree
            .which("padolang")
            .or_else(|| Self::lancador_do_repositorio(worktree))
            .ok_or_else(|| {
                "padolang não encontrado: coloque a pasta bin/ do repositório da padolang no PATH \
                 (ou defina lsp.padolang-lsp.binary.path nas configurações do Zed)"
                    .to_string()
            })?;

        Ok(zed::Command {
            command: comando,
            args: vec!["lsp".into()],
            env,
        })
    }
}

zed::register_extension!(Padolang);

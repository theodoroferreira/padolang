// Extensão da padolang para VS Code e Cursor.
//
// As cores básicas vêm da gramática TextMate (syntaxes/), que funciona mesmo sem Java.
// Diagnósticos, cores semânticas e formatação vêm do servidor LSP: "java -jar padolang.jar lsp".
// O comando "Padolang: Executar arquivo" roda "java -jar padolang.jar run <arquivo>" num terminal
// integrado (e não no painel Output) porque o padoIN lê do teclado.

import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";
import { LanguageClient, LanguageClientOptions, ServerOptions } from "vscode-languageclient/node";

let cliente: LanguageClient | undefined;
let terminal: vscode.Terminal | undefined;

export async function activate(contexto: vscode.ExtensionContext): Promise<void> {
  contexto.subscriptions.push(
    vscode.commands.registerCommand("padolang.executar", (uri?: vscode.Uri) => executar(contexto, uri)),
    vscode.commands.registerCommand("padolang.reiniciar", () => reiniciar(contexto)),
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration("padolang.java") || e.affectsConfiguration("padolang.jar")) {
        reiniciar(contexto);
      }
    }),
    vscode.window.onDidCloseTerminal((t) => {
      if (t === terminal) {
        terminal = undefined;
      }
    }),
  );
  await iniciar(contexto);
}

export async function deactivate(): Promise<void> {
  await parar();
}

// ------------------------------------------------------------------ servidor LSP

async function iniciar(contexto: vscode.ExtensionContext): Promise<void> {
  const java = comandoJava();
  const jar = caminhoJar(contexto);
  if (!jar) {
    return;
  }

  // Sem "transport": o cliente usa stdin/stdout sem acrescentar "--stdio" aos argumentos
  const servidor: ServerOptions = { command: java, args: ["-jar", jar, "lsp"] };
  const opcoes: LanguageClientOptions = {
    documentSelector: [{ language: "padolang" }],
  };

  cliente = new LanguageClient("padolang", "Padolang LSP", servidor, opcoes);
  try {
    await cliente.start();
  } catch (e) {
    cliente = undefined;
    const escolha = await vscode.window.showErrorMessage(
      `Não foi possível iniciar o servidor da padolang (${java} -jar ${jar} lsp). ` +
        "É preciso ter o Java 21 ou mais novo; o caminho dele pode ser indicado em padolang.java.",
      "Abrir configurações",
    );
    if (escolha) {
      vscode.commands.executeCommand("workbench.action.openSettings", "padolang");
    }
  }
}

async function parar(): Promise<void> {
  const c = cliente;
  cliente = undefined;
  if (c) {
    await c.stop();
  }
}

async function reiniciar(contexto: vscode.ExtensionContext): Promise<void> {
  await parar();
  await iniciar(contexto);
}

// ------------------------------------------------------------------ executar arquivo

async function executar(contexto: vscode.ExtensionContext, uri?: vscode.Uri): Promise<void> {
  const documento = uri
    ? await vscode.workspace.openTextDocument(uri)
    : vscode.window.activeTextEditor?.document;
  if (!documento || documento.languageId !== "padolang") {
    vscode.window.showErrorMessage("Abra um arquivo .padoin para executar.");
    return;
  }
  if (documento.isUntitled) {
    vscode.window.showErrorMessage("Salve o arquivo antes de executar.");
    return;
  }
  if (documento.isDirty && !(await documento.save())) {
    return;
  }

  const jar = caminhoJar(contexto);
  if (!jar) {
    return;
  }

  // Uma execução por vez: rodar de novo fecha a anterior (que pode estar esperando um padoIN)
  terminal?.dispose();
  const arquivo = documento.uri.fsPath;
  terminal = vscode.window.createTerminal({
    name: `padolang: ${path.basename(arquivo)}`,
    shellPath: comandoJava(),
    shellArgs: ["-jar", jar, "run", arquivo],
    cwd: path.dirname(arquivo),
  });
  terminal.show();
}

// ------------------------------------------------------------------ configuração

function comandoJava(): string {
  return vscode.workspace.getConfiguration("padolang").get<string>("java")?.trim() || "java";
}

/** O jar configurado em padolang.jar, ou o que vem com a extensão. Avisa e devolve undefined se não existir. */
function caminhoJar(contexto: vscode.ExtensionContext): string | undefined {
  let jar = vscode.workspace.getConfiguration("padolang").get<string>("jar")?.trim() ?? "";
  if (jar) {
    const pasta = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? "";
    jar = jar.replace(/\$\{workspaceFolder\}/g, pasta);
  } else {
    jar = contexto.asAbsolutePath(path.join("server", "padolang.jar"));
  }

  if (!fs.existsSync(jar)) {
    vscode.window
      .showErrorMessage(`padolang.jar não encontrado em ${jar}. Confira a configuração padolang.jar.`, "Abrir configurações")
      .then((escolha) => {
        if (escolha) {
          vscode.commands.executeCommand("workbench.action.openSettings", "padolang.jar");
        }
      });
    return undefined;
  }
  return jar;
}

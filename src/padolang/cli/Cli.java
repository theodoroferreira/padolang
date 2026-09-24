package padolang.cli;

import padolang.ast.AstPrinter;
import padolang.ast.Programa;
import padolang.diagnostico.CompilacaoException;
import padolang.diagnostico.Diagnostico;
import padolang.gerador.GeradorC;
import padolang.lexer.Lexer;
import padolang.lexer.Token;
import padolang.parser.Parser;
import padolang.semantica.AnalisadorSemantico;
import padolang.semantica.Anotacoes;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Linha de comando da padolang, com subcomandos:
 *
 *   padolang check  <arquivo> [--json]      léxico + sintático + semântico, sem gerar C
 *   padolang build  <arquivo> [-o saida.c]  gera o C
 *   padolang tokens <arquivo> [--editor]    lista os tokens
 *   padolang ast    <arquivo>               mostra a árvore sintática
 *   padolang --help | --version
 *
 * Compatibilidade: se o primeiro argumento termina em ".padoin", vale a forma antiga
 * (<arquivo.padoin> [-o saida.c] [--tokens] [--ast] [--editor]), usada pelo padolang.sh e pelo testar.sh.
 *
 * Códigos de saída: 0 ok, 1 erro no programa padolang, 2 erro de uso ou de arquivo.
 */
public class Cli {

    public static final String VERSAO = "0.1.0";

    static final int OK = 0;
    static final int ERRO_PROGRAMA = 1;
    static final int ERRO_USO = 2;

    private static final String AJUDA = """
            Uso: padolang <comando> [opções]

            Comandos:
              check  <arquivo> [--json]      confere o programa (léxico, sintático e semântico), sem gerar C
              build  <arquivo> [-o saida.c]  gera o C (sem -o, ao lado do fonte, com a extensão .c)
              tokens <arquivo> [--editor]    lista os tokens (--editor: comentários e erros também)
              ast    <arquivo>               mostra a árvore sintática

            Opções:
              -h, --help       mostra esta ajuda
              -v, --version    mostra a versão

            Forma antiga, ainda aceita: padolang <arquivo.padoin> [-o saida.c] [--tokens] [--ast]
            Códigos de saída: 0 ok, 1 erro no programa, 2 erro de uso ou de arquivo.""";

    private final PrintStream out;
    private final PrintStream err;

    public Cli(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /** Executa a linha de comando e devolve o código de saída. */
    public int executar(String[] args) {
        if (args.length == 0) {
            err.println(AJUDA);
            return ERRO_USO;
        }
        if (args[0].endsWith(".padoin")) {
            return legado(args);
        }
        List<String> resto = List.of(args).subList(1, args.length);
        return switch (args[0]) {
            case "-h", "--help", "help" -> { out.println(AJUDA); yield OK; }
            case "-v", "--version", "version" -> { out.println("padolang " + VERSAO); yield OK; }
            case "check" -> check(resto);
            case "build" -> build(resto);
            case "tokens" -> tokens(resto);
            case "ast" -> ast(resto);
            case "run", "fmt", "lsp" -> {
                err.println("padolang " + args[0] + ": ainda não implementado");
                yield ERRO_USO;
            }
            default -> {
                err.println("padolang: comando desconhecido: " + args[0]);
                err.println("Use \"padolang --help\" para ver os comandos.");
                yield ERRO_USO;
            }
        };
    }

    // ------------------------------------------------------------------ subcomandos

    private int check(List<String> args) {
        Argumentos a = Argumentos.ler("check", args, Set.of("--json"), Set.of(), err);
        if (a == null) return ERRO_USO;
        String fonte = ler(a.arquivo);
        if (fonte == null) return ERRO_USO;

        List<Diagnostico> diagnosticos = List.of();
        try {
            Programa ast = new Parser(new Lexer(fonte).tokenizar()).programa();
            new AnalisadorSemantico().analisar(ast);
        } catch (CompilacaoException e) {
            diagnosticos = e.diagnosticos();
        }

        if (a.flags.contains("--json")) {
            out.println(Json.diagnosticos(diagnosticos));
        } else if (diagnosticos.isEmpty()) {
            err.println(a.arquivo + ": sem erros");
        } else {
            diagnosticos.forEach(d -> err.println(d.formatar()));
        }
        return diagnosticos.isEmpty() ? OK : ERRO_PROGRAMA;
    }

    private int build(List<String> args) {
        Argumentos a = Argumentos.ler("build", args, Set.of(), Set.of("-o"), err);
        if (a == null) return ERRO_USO;
        Path saida = a.valores.containsKey("-o") ? Path.of(a.valores.get("-o")) : saidaPadrao(a.arquivo);
        return compilar(a.arquivo, saida, false, false);
    }

    private int tokens(List<String> args) {
        Argumentos a = Argumentos.ler("tokens", args, Set.of("--editor"), Set.of(), err);
        if (a == null) return ERRO_USO;
        String fonte = ler(a.arquivo);
        if (fonte == null) return ERRO_USO;
        if (a.flags.contains("--editor")) {
            return tokensEditor(fonte);
        }
        try {
            imprimirTokens(new Lexer(fonte).tokenizar());
            return OK;
        } catch (CompilacaoException e) {
            err.println(e.getMessage());
            return ERRO_PROGRAMA;
        }
    }

    private int ast(List<String> args) {
        Argumentos a = Argumentos.ler("ast", args, Set.of(), Set.of(), err);
        if (a == null) return ERRO_USO;
        String fonte = ler(a.arquivo);
        if (fonte == null) return ERRO_USO;
        try {
            imprimirAst(new Parser(new Lexer(fonte).tokenizar()).programa());
            return OK;
        } catch (CompilacaoException e) {
            err.println(e.getMessage());
            return ERRO_PROGRAMA;
        }
    }

    /** A forma antiga: <arquivo.padoin> [-o saida.c] [--tokens] [--ast] [--editor]. */
    private int legado(String[] args) {
        List<String> opcoes = List.of(args);
        Path saida = saidaPadrao(args[0]);
        int o = opcoes.indexOf("-o");
        if (o >= 0 && o + 1 < args.length) {
            saida = Path.of(args[o + 1]);
        }
        if (opcoes.contains("--editor")) {
            String fonte = ler(args[0]);
            return fonte == null ? ERRO_USO : tokensEditor(fonte);
        }
        return compilar(args[0], saida, opcoes.contains("--tokens"), opcoes.contains("--ast"));
    }

    // ------------------------------------------------------------------ pipeline

    /** LÉXICO -> TOKENS -> PARSER -> AST -> SEMÂNTICA -> CÓDIGO C, gravado em saida. */
    private int compilar(String arquivo, Path saida, boolean mostrarTokens, boolean mostrarAst) {
        String fonte = ler(arquivo);
        if (fonte == null) return ERRO_USO;
        try {
            List<Token> tokens = new Lexer(fonte).tokenizar();
            if (mostrarTokens) imprimirTokens(tokens);

            Programa ast = new Parser(tokens).programa();
            if (mostrarAst) imprimirAst(ast);

            // só gera código se o programa fizer sentido
            Anotacoes tipos = new AnalisadorSemantico().analisar(ast);

            String codigoC = new GeradorC(tipos).gerar(ast);
            if (saida.getParent() != null) {
                Files.createDirectories(saida.getParent());
            }
            Files.writeString(saida, codigoC, StandardCharsets.UTF_8);
            err.println("C gerado em " + saida);
            return OK;
        } catch (CompilacaoException e) {   // léxico, sintático ou semântico
            err.println(e.getMessage());
            return ERRO_PROGRAMA;
        } catch (IOException e) {
            err.println("padoERRO ao escrever " + saida + ": " + e.getMessage());
            return ERRO_USO;
        }
    }

    private void imprimirTokens(List<Token> tokens) {
        out.println("== TOKENS ==");
        out.printf("%-5s %-5s %-13s %s%n", "LIN", "COL", "TIPO", "LEXEMA");
        tokens.forEach(out::println);
        out.println();
    }

    private void imprimirAst(Programa ast) {
        out.println("== AST ==");
        out.print(AstPrinter.imprimir(ast));
        out.println();
    }

    /** Lista os tokens do modo EDITOR e os erros léxicos; devolve 1 se houver algum erro. */
    private int tokensEditor(String fonte) {
        Lexer lexer = new Lexer(fonte, Lexer.Modo.EDITOR);
        List<Token> tokens = lexer.tokenizar();
        out.println("== TOKENS (editor) ==");
        out.printf("%-5s %-5s %-4s %-13s %s%n", "LIN", "COL", "TAM", "TIPO", "LEXEMA");
        for (Token t : tokens) {
            out.printf("%-5d %-5d %-4d %-13s %s%n", t.linha(), t.coluna(), t.tamanho(), t.tipo(), t.lexema());
        }
        for (Diagnostico d : lexer.erros()) {
            out.println(d.codigo() + " " + d.formatar());
        }
        return lexer.erros().isEmpty() ? OK : ERRO_PROGRAMA;
    }

    // ------------------------------------------------------------------ auxiliares

    private static Path saidaPadrao(String arquivo) {
        return Path.of(arquivo.replaceAll("\\.padoin$", "") + ".c");
    }

    /** Lê o fonte em UTF-8; em caso de falha mostra o erro e devolve null. */
    private String ler(String arquivo) {
        try {
            return Files.readString(Path.of(arquivo), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            err.println("padoERRO ao ler " + arquivo + ": arquivo não encontrado");
            return null;
        } catch (IOException e) {
            err.println("padoERRO ao ler " + arquivo + ": " + e.getMessage());
            return null;
        }
    }

    /** Argumentos de um subcomando: exatamente um arquivo, flags sem valor e opções com valor. */
    private record Argumentos(String arquivo, Set<String> flags, Map<String, String> valores) {

        /** Devolve null (depois de mostrar o erro) se os argumentos não servem para o comando. */
        static Argumentos ler(String comando, List<String> args, Set<String> flagsValidas,
                              Set<String> opcoesComValor, PrintStream err) {
            List<String> arquivos = new ArrayList<>();
            Set<String> flags = new HashSet<>();
            Map<String, String> valores = new HashMap<>();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (opcoesComValor.contains(arg)) {
                    if (i + 1 >= args.size()) {
                        err.println("padolang " + comando + ": falta o valor de " + arg);
                        return null;
                    }
                    valores.put(arg, args.get(++i));
                } else if (flagsValidas.contains(arg)) {
                    flags.add(arg);
                } else if (arg.startsWith("-")) {
                    err.println("padolang " + comando + ": opção desconhecida: " + arg);
                    return null;
                } else {
                    arquivos.add(arg);
                }
            }
            if (arquivos.size() != 1) {
                err.println("padolang " + comando + ": informe exatamente um arquivo .padoin");
                return null;
            }
            return new Argumentos(arquivos.get(0), flags, valores);
        }
    }
}

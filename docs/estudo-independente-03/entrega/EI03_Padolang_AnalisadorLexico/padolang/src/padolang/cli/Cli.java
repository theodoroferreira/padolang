package padolang.cli;

import padolang.ast.AstPrinter;
import padolang.ast.Programa;
import padolang.diagnostico.CompilacaoException;
import padolang.diagnostico.Diagnostico;
import padolang.formatador.Formatador;
import padolang.gerador.GeradorC;
import padolang.lexer.Lexer;
import padolang.lexer.Token;
import padolang.lsp.ServidorLsp;
import padolang.parser.Parser;
import padolang.runner.Runner;
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
 *   padolang run    <arquivo> [--manter | --dir pasta] [--tokens] [--ast]
 *                                           gera o C, compila com gcc e executa
 *   padolang fmt    <arquivo...> [--check | --stdout]
 *                                           formata os arquivos (no lugar, ou só confere, ou imprime)
 *   padolang lsp                            servidor LSP por stdin/stdout (para os editores)
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
              run    <arquivo> [opções]      gera o C, compila (PADOLANG_CC, gcc ou cc) e executa
                     --manter                guarda o .c e o executável em saida/ (senão, numa pasta temporária)
                     --dir <pasta>           guarda o .c e o executável nessa pasta
                     --tokens, --ast         mostra os tokens ou a árvore antes de executar
              fmt    <arquivo...> [opções]   formata os arquivos, gravando no próprio arquivo
                     --check                 só confere: sai com 1 se algum arquivo mudaria
                     --stdout                só imprime o resultado, sem gravar
              lsp                            servidor LSP por stdin/stdout (usado pelos editores)
              tokens <arquivo> [--editor]    lista os tokens (--editor: comentários e todos os erros léxicos)
              ast    <arquivo>               mostra a árvore sintática

            Opções:
              -h, --help       mostra esta ajuda
              -v, --version    mostra a versão

            Forma antiga, ainda aceita: padolang <arquivo.padoin> [-o saida.c] [--tokens] [--ast]
            Códigos de saída: 0 ok, 1 erro no programa, 2 erro de uso ou de arquivo.
            No run, o código de saída é o do programa (1 em erro de execução).""";

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
            case "run" -> run(resto);
            case "fmt" -> fmt(resto);
            case "lsp" -> lsp(resto);
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
        return compilar(a.arquivo, saida, false, false, true);
    }

    private int run(List<String> args) {
        Argumentos a = Argumentos.ler("run", args, Set.of("--manter", "--tokens", "--ast"), Set.of("--dir"), err);
        if (a == null) return ERRO_USO;
        String nome = Path.of(a.arquivo).getFileName().toString().replaceAll("\\.padoin$", "");
        boolean manter = a.flags.contains("--manter") || a.valores.containsKey("--dir");
        try {
            Path pasta = a.valores.containsKey("--dir") ? Path.of(a.valores.get("--dir"))
                    : manter ? Path.of("saida")
                    : Files.createTempDirectory("padolang");
            Path codigoC = pasta.resolve(nome + ".c");
            if (!manter) {
                // apagados quando a JVM encerra, inclusive por Ctrl+C ou timeout (a pasta por último)
                pasta.toFile().deleteOnExit();
                codigoC.toFile().deleteOnExit();
                Runner.executavelPara(codigoC).toFile().deleteOnExit();
            }
            int transpilacao = compilar(a.arquivo, codigoC,
                    a.flags.contains("--tokens"), a.flags.contains("--ast"), manter);
            if (transpilacao != OK) return transpilacao;
            return new Runner(err).compilarEExecutar(codigoC, nome);
        } catch (IOException e) {
            err.println("padoERRO ao executar " + a.arquivo + ": " + e.getMessage());
            return ERRO_USO;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ERRO_USO;
        }
    }

    private int fmt(List<String> args) {
        List<String> arquivos = new ArrayList<>();
        boolean check = false, stdout = false;
        for (String arg : args) {
            switch (arg) {
                case "--check" -> check = true;
                case "--stdout" -> stdout = true;
                default -> {
                    if (arg.startsWith("-")) {
                        err.println("padolang fmt: opção desconhecida: " + arg);
                        return ERRO_USO;
                    }
                    arquivos.add(arg);
                }
            }
        }
        if (check && stdout) {
            err.println("padolang fmt: use --check ou --stdout, não os dois");
            return ERRO_USO;
        }
        if (arquivos.isEmpty()) {
            err.println("padolang fmt: informe ao menos um arquivo .padoin");
            return ERRO_USO;
        }

        int codigo = OK;
        for (String arquivo : arquivos) {
            codigo = Math.max(codigo, formatarArquivo(arquivo, check, stdout));
        }
        return codigo;
    }

    /** Formata um arquivo; devolve 1 se ele tem erro (ou, com --check, se mudaria) e 2 se não deu para ler ou gravar. */
    private int formatarArquivo(String arquivo, boolean check, boolean stdout) {
        String fonte = ler(arquivo);
        if (fonte == null) return ERRO_USO;
        String formatado;
        try {
            formatado = Formatador.formatar(fonte);
        } catch (CompilacaoException e) {       // léxico ou sintático: o arquivo fica como está
            err.println(arquivo + ": " + e.getMessage());
            return ERRO_PROGRAMA;
        } catch (IllegalStateException e) {     // a checagem de segurança do formatador falhou
            err.println("padoERRO em " + arquivo + ": " + e.getMessage());
            return ERRO_USO;
        }

        if (stdout) {
            out.print(formatado);
            out.flush();
            return OK;
        }
        if (formatado.equals(fonte)) {
            return OK;
        }
        if (check) {
            err.println(arquivo + ": precisa ser formatado");
            return ERRO_PROGRAMA;
        }
        try {
            Files.writeString(Path.of(arquivo), formatado, StandardCharsets.UTF_8);
            err.println(arquivo + ": formatado");
            return OK;
        } catch (IOException e) {
            err.println("padoERRO ao escrever " + arquivo + ": " + e.getMessage());
            return ERRO_USO;
        }
    }

    /** O stdout é só do protocolo: o servidor escreve bytes crus nele; o log vai para o stderr. */
    private int lsp(List<String> args) {
        for (String arg : args) {
            if (!arg.equals("--stdio")) {           // --stdio: o que alguns clientes passam; é o único transporte
                err.println("padolang lsp: opção desconhecida: " + arg);
                return ERRO_USO;
            }
        }
        return new ServidorLsp(System.in, out, err).executar();
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
        return compilar(args[0], saida, opcoes.contains("--tokens"), opcoes.contains("--ast"), true);
    }

    // ------------------------------------------------------------------ pipeline

    /** LÉXICO -> TOKENS -> PARSER -> AST -> SEMÂNTICA -> CÓDIGO C, gravado em saida.
     *  Com avisar, mostra no stderr onde o C foi gravado. */
    private int compilar(String arquivo, Path saida, boolean mostrarTokens, boolean mostrarAst, boolean avisar) {
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
            if (avisar) err.println("C gerado em " + saida);
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

    /**
     * Lista os tokens do modo EDITOR e, depois deles, os erros léxicos, cada um com o código, a
     * posição, a mensagem e o lexema apontado; devolve 1 se houver algum erro.
     */
    private int tokensEditor(String fonte) {
        Lexer lexer = new Lexer(fonte, Lexer.Modo.EDITOR);
        List<Token> tokens = lexer.tokenizar();
        out.println("== TOKENS (editor) ==");
        out.printf("%-5s %-5s %-4s %-13s %s%n", "LIN", "COL", "TAM", "TIPO", "LEXEMA");
        for (Token t : tokens) {
            out.printf("%-5d %-5d %-4d %-13s %s%n", t.linha(), t.coluna(), t.tamanho(), t.tipo(), t.lexemaEmUmaLinha());
        }
        for (Diagnostico d : lexer.erros()) {
            out.println(d.codigo() + " " + d.formatar() + " [lexema: " + trecho(fonte, d) + "]");
        }
        return lexer.erros().isEmpty() ? OK : ERRO_PROGRAMA;
    }

    /** O trecho do fonte que o diagnóstico aponta (linha, coluna e tamanho contam code points). */
    private static String trecho(String fonte, Diagnostico d) {
        if (fonte.startsWith("\uFEFF")) {
            fonte = fonte.substring(1);             // o léxico não conta o BOM como coluna
        }
        int[] linha = fonte.split("\n", -1)[d.linha() - 1].codePoints().toArray();
        int inicio = Math.min(d.coluna() - 1, linha.length);
        return new String(linha, inicio, Math.min(d.tamanho(), linha.length - inicio));
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

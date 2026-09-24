package padolang;

import padolang.ast.AstPrinter;
import padolang.ast.Programa;
import padolang.gerador.GeradorC;
import padolang.lexer.Lexer;
import padolang.lexer.LexicoException;
import padolang.lexer.Token;
import padolang.parser.Parser;
import padolang.parser.SintaticoException;
import padolang.semantica.AnalisadorSemantico;
import padolang.semantica.SemanticoException;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Transpilador Padolang -> C.
 *
 *   java -cp out padolang.Main <arquivo.padoin> [-o saida.c] [--tokens] [--ast]
 *
 * Pipeline: LÉXICO -> TOKENS -> PARSER -> AST -> SEMÂNTICA -> CÓDIGO C
 * Sem "-o", o C é gerado ao lado do fonte, com a extensão .c.
 */
public class Main {

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);

        List<String> opcoes = List.of(args);
        if (args.length < 1 || !args[0].endsWith(".padoin")) {
            err.println("Uso: padolang <arquivo.padoin> [-o saida.c] [--tokens] [--ast]");
            System.exit(2);
        }

        Path entrada = Path.of(args[0]);
        Path saida = Path.of(args[0].replaceAll("\\.padoin$", ".c"));
        int o = opcoes.indexOf("-o");
        if (o >= 0 && o + 1 < args.length) {
            saida = Path.of(args[o + 1]);
        }

        String fonte;
        try {
            fonte = Files.readString(entrada, StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("Erro ao ler " + entrada + ": " + e.getMessage());
            System.exit(2);
            return;
        }

        try {
            // 1. Análise léxica
            List<Token> tokens = new Lexer(fonte).tokenizar();
            if (opcoes.contains("--tokens")) {
                out.println("== TOKENS ==");
                out.printf("%-5s %-5s %-13s %s%n", "LIN", "COL", "TIPO", "LEXEMA");
                tokens.forEach(out::println);
                out.println();
            }

            // 2. Análise sintática -> AST
            Programa ast = new Parser(tokens).programa();
            if (opcoes.contains("--ast")) {
                out.println("== AST ==");
                out.print(AstPrinter.imprimir(ast));
                out.println();
            }

            // 3. Análise semântica: só gera código se o programa fizer sentido
            new AnalisadorSemantico().analisar(ast);

            // 4. Geração de código
            String codigoC = new GeradorC().gerar(ast);
            if (saida.getParent() != null) {
                Files.createDirectories(saida.getParent());
            }
            Files.writeString(saida, codigoC, StandardCharsets.UTF_8);
            err.println("C gerado em " + saida);

        } catch (LexicoException | SintaticoException | SemanticoException e) {
            err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            err.println("Erro ao escrever " + saida + ": " + e.getMessage());
            System.exit(2);
        }
    }
}

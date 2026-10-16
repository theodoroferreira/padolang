package padolang;

import padolang.cli.Cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Ponto de entrada do transpilador Padolang -> C. Só delega para a Cli (padolang.cli.Cli),
 * que tem os subcomandos (check, build, tokens, ast) e aceita também a forma antiga:
 *
 *   java -cp out padolang.Main <arquivo.padoin> [-o saida.c] [--tokens] [--ast]
 *   java -jar out/padolang.jar check <arquivo.padoin> [--json]
 */
public class Main {

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.exit(new Cli(out, err).executar(args));
    }
}

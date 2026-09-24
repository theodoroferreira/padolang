package padolang.runner;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compila o C gerado com um compilador C e executa o programa (o "run" da CLI).
 *
 * O compilador é o da variável PADOLANG_CC, se existir; senão gcc; senão cc. As opções são as
 * mesmas do padolang.sh. O programa roda com a entrada e a saída do terminal (inheritIO), porque
 * o padoIN lê do teclado e o testar.sh redireciona o .entrada; o código de saída é o do programa.
 */
public class Runner {

    /** Código de saída quando falta o compilador C ou ele recusa o código gerado. */
    public static final int ERRO_FERRAMENTA = 2;

    /** -fwrapv: estouro de inteiro dá a volta, como no Java.
     *  Avisos de variável/parâmetro não usado são desligados: não são erros do programa padolang. */
    private static final List<String> OPCOES_CC = List.of(
            "-std=c11", "-fwrapv", "-Wall", "-Wextra",
            "-Wno-unused-variable", "-Wno-unused-parameter", "-Wno-unused-but-set-variable");

    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

    private final PrintStream err;

    public Runner(PrintStream err) {
        this.err = err;
    }

    /** Nome do executável gerado ao lado do .c (com .exe no Windows). */
    public static Path executavelPara(Path codigoC) {
        String nome = codigoC.getFileName().toString().replaceAll("\\.c$", "");
        return codigoC.resolveSibling(WINDOWS ? nome + ".exe" : nome);
    }

    /** Compila codigoC e executa o programa; devolve o código de saída do programa. */
    public int compilarEExecutar(Path codigoC, String nome) throws IOException, InterruptedException {
        Optional<String> cc = compiladorC();
        if (cc.isEmpty()) {
            err.println("padoERRO: compilador C não encontrado (instale o gcc ou defina PADOLANG_CC)");
            return ERRO_FERRAMENTA;
        }

        Path exe = executavelPara(codigoC);
        List<String> comando = new ArrayList<>();
        comando.add(cc.get());
        comando.addAll(OPCOES_CC);
        comando.addAll(List.of("-o", exe.toString(), codigoC.toString()));
        // Os avisos e erros do compilador C vão para o stderr; o stdout fica só para o programa.
        Process gcc = new ProcessBuilder(comando)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        if (gcc.waitFor() != 0) {
            err.println("padoERRO: o compilador C (" + cc.get() + ") recusou o código gerado em " + codigoC);
            return ERRO_FERRAMENTA;
        }

        err.println("---------- executando " + nome + " ----------");
        Process programa = new ProcessBuilder(exe.toAbsolutePath().toString()).inheritIO().start();
        // Se a JVM for encerrada (Ctrl+C, o timeout do testar.sh), o programa não fica órfão.
        Thread encerrar = new Thread(programa::destroyForcibly);
        Runtime.getRuntime().addShutdownHook(encerrar);
        try {
            return programa.waitFor();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(encerrar);
            } catch (IllegalStateException e) {
                // a JVM já está encerrando: o gancho vai rodar de qualquer jeito
            }
        }
    }

    /** PADOLANG_CC, gcc ou cc, o primeiro que existir. */
    static Optional<String> compiladorC() {
        String escolhido = System.getenv("PADOLANG_CC");
        if (escolhido != null && !escolhido.isBlank()) {
            return procurar(escolhido.strip());
        }
        return procurar("gcc").or(() -> procurar("cc"));
    }

    /** Caminho do executável: o próprio nome, se tiver uma pasta, ou procurado no PATH. */
    private static Optional<String> procurar(String nome) {
        if (nome.contains("/") || nome.contains(File.separator)) {
            return Files.isExecutable(Path.of(nome)) ? Optional.of(nome) : Optional.empty();
        }
        String path = System.getenv("PATH");
        if (path == null) return Optional.empty();
        for (String pasta : path.split(File.pathSeparator)) {
            if (pasta.isEmpty()) continue;
            for (String candidato : WINDOWS ? List.of(nome + ".exe", nome) : List.of(nome)) {
                Path p = Path.of(pasta, candidato);
                if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                    return Optional.of(p.toString());
                }
            }
        }
        return Optional.empty();
    }
}

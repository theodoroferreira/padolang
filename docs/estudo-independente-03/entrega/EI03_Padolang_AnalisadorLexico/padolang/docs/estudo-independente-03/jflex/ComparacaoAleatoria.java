import padolang.lexer.Lexer;
import padolang.lexer.Token;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Compara o Lexer.java (modo EDITOR) com o scanner gerado pelo JFlex a partir do padolang.flex,
 * em entradas aleatórias: os dois precisam listar os mesmos tokens (classe, lexema, linha e coluna).
 *
 * As entradas são sequências de pedaços que provocam os conflitos e os erros do léxico: prefixos
 * de palavras reservadas, dígitos e pontos, aspas e barras, '#' e '*', operadores, quebras de
 * linha, o 👍 com e sem tom de pele e símbolos fora do alfabeto. A semente é fixa.
 *
 *   java ComparacaoAleatoria <quantidade de entradas>
 */
public class ComparacaoAleatoria {

    private static final String[] PEDACOS = {
            "a", "x", "_", "P", "pad", "oin", "padoin", "padoIN", "padoPRINT", "LN", "quanto",
            "0", "1", "9", ".", ".", "\"", "\"", "\\", "n", "t", "q", "#", "#", "*", "*",
            "\n", "\n", "\r\n", " ", "\t", "+", "-", "=", "!", "<", ">", "/", "(", ")", "{", "}", ",",
            "👍", "\uD83C\uDFFD", "\uFE0F", "ç", "@", ";", "&"};

    public static void main(String[] args) throws Exception {
        int quantidade = Integer.parseInt(args[0]);
        Random sorteio = new Random(42);
        int diferentes = 0;

        for (int i = 0; i < quantidade; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = sorteio.nextInt(25); k > 0; k--) {
                sb.append(PEDACOS[sorteio.nextInt(PEDACOS.length)]);
            }
            String fonte = sb.toString();

            List<String> manual = new ArrayList<>();
            for (Token t : new Lexer(fonte, Lexer.Modo.EDITOR).tokenizar()) {
                manual.add(t.toString());
            }
            List<String> jflex = new ArrayList<>();
            EspecificacaoLexica scanner = new EspecificacaoLexica(new StringReader(fonte));
            for (String linha = scanner.yylex(); linha != null; linha = scanner.yylex()) {
                jflex.add(linha);
            }

            if (!manual.equals(jflex) && diferentes++ < 5) {
                System.out.println("DIFERENTE: " + fonte.replace("\r", "\\r").replace("\n", "\\n"));
                System.out.println("  Lexer.java: " + manual);
                System.out.println("  JFlex:      " + jflex);
            }
        }
        System.out.println(diferentes + " diferenças em " + quantidade + " entradas aleatórias");
        System.exit(diferentes == 0 ? 0 : 1);
    }
}

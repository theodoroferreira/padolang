package padolang.lexer;

/**
 * Um token reconhecido: categoria, texto (lexema), posição no fonte e tamanho.
 *
 * O tamanho conta code points no fonte original, que pode diferir do lexema: o 👍 com tom de pele
 * (👍🏽) tem lexema "👍" mas ocupa 2 code points. O EOF tem tamanho 0.
 */
public record Token(TokenType tipo, String lexema, int linha, int coluna, int tamanho) {
    @Override
    public String toString() {
        return String.format("%-5d %-5d %-13s %s", linha, coluna, tipo, lexemaEmUmaLinha());
    }

    /** O lexema para listagens: as quebras de linha de um comentário de bloco aparecem como "\n". */
    public String lexemaEmUmaLinha() {
        return lexema.replace("\r", "").replace("\n", "\\n");
    }
}

package padolang.lexer;

/** Um token reconhecido: categoria, texto original (lexema) e posição no fonte. */
public record Token(TokenType tipo, String lexema, int linha, int coluna) {
    @Override
    public String toString() {
        return String.format("%-5d %-5d %-13s %s", linha, coluna, tipo, lexema);
    }
}

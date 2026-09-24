package padolang.parser;

import padolang.lexer.Token;

/** Erro sintático: a sequência de tokens viola a GLC. */
public class SintaticoException extends RuntimeException {
    private final Token encontrado;

    public SintaticoException(String esperado, Token encontrado) {
        super(String.format("Erro sintático (linha %d, coluna %d): esperado %s, mas encontrado %s",
                encontrado.linha(), encontrado.coluna(), esperado, descrever(encontrado)));
        this.encontrado = encontrado;
    }

    public Token getEncontrado() {
        return encontrado;
    }

    private static String descrever(Token t) {
        return switch (t.tipo()) {
            case EOF -> "fim do arquivo";
            case ID, NUM_INT, NUM_FLOAT, STRING -> t.tipo().descricao() + " '" + t.lexema() + "'";
            default -> "'" + t.lexema() + "'";
        };
    }
}

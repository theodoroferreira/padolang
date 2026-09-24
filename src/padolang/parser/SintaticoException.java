package padolang.parser;

import padolang.lexer.Token;

/** Erro sintático: a sequência de tokens viola a GLC. */
public class SintaticoException extends RuntimeException {

    public SintaticoException(String esperado, Token encontrado) {
        super(String.format("padoERRO sintático (linha %d, coluna %d): esperado %s, mas encontrado %s",
                encontrado.linha(), encontrado.coluna(), esperado, descrever(encontrado)));
    }

    private static String descrever(Token t) {
        return switch (t.tipo()) {
            case EOF -> "fim do arquivo";
            case ID, NUM_INT, NUM_FLOAT, STRING -> t.tipo().descricao() + " '" + t.lexema() + "'";
            default -> "'" + t.lexema() + "'";
        };
    }
}

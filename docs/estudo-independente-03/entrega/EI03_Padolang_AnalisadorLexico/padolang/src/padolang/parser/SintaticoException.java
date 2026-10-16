package padolang.parser;

import padolang.diagnostico.CompilacaoException;
import padolang.diagnostico.Diagnostico;
import padolang.diagnostico.Fase;
import padolang.lexer.Token;

import java.util.List;

/** Erro sintático (S1): a sequência de tokens viola a GLC. Aponta para o token encontrado. */
public class SintaticoException extends CompilacaoException {

    public SintaticoException(String esperado, Token encontrado) {
        super(List.of(Diagnostico.erro(Fase.SINTATICO, "S1", encontrado.linha(), encontrado.coluna(),
                encontrado.tamanho(), "esperado " + esperado + ", mas encontrado " + descrever(encontrado))));
    }

    public Diagnostico diagnostico() {
        return diagnosticos().get(0);
    }

    private static String descrever(Token t) {
        return switch (t.tipo()) {
            case EOF -> "fim do arquivo";
            case ID, NUM_INT, NUM_FLOAT, STRING -> t.tipo().descricao() + " '" + t.lexema() + "'";
            default -> "'" + t.lexema() + "'";
        };
    }
}

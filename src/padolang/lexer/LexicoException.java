package padolang.lexer;

import padolang.diagnostico.CompilacaoException;
import padolang.diagnostico.Diagnostico;
import padolang.diagnostico.Fase;

import java.util.List;

/** Erro léxico: símbolo ou lexema que não pertence a nenhum padrão de token. */
public class LexicoException extends CompilacaoException {

    public LexicoException(Diagnostico diagnostico) {
        super(List.of(diagnostico));
    }

    public LexicoException(String codigo, String mensagem, int linha, int coluna, int tamanho) {
        this(Diagnostico.erro(Fase.LEXICO, codigo, linha, coluna, tamanho, mensagem));
    }

    public Diagnostico diagnostico() {
        return diagnosticos().get(0);
    }
}

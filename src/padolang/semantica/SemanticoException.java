package padolang.semantica;

import padolang.diagnostico.CompilacaoException;
import padolang.diagnostico.Diagnostico;

import java.util.List;

/**
 * Erros semânticos: o programa está bem formado (passou pelo parser), mas não faz sentido
 * (variável inexistente, tipos incompatíveis, padOUT fora de função...).
 *
 * Ao contrário do léxico e do sintático, que param no primeiro erro, a semântica percorre o
 * programa inteiro e junta todos os erros. Cada um vira uma linha da mensagem.
 */
public class SemanticoException extends CompilacaoException {

    public SemanticoException(List<Diagnostico> erros) {
        super(erros);
    }
}

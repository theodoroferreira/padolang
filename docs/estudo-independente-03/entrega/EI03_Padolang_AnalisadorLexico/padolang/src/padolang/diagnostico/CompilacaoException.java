package padolang.diagnostico;

import java.util.List;

/**
 * Base dos erros de compilação (léxico, sintático e semântico). Cada um carrega os diagnósticos
 * estruturados; a mensagem é o texto formatado deles, um por linha.
 */
public abstract class CompilacaoException extends RuntimeException {

    private final List<Diagnostico> diagnosticos;

    protected CompilacaoException(List<Diagnostico> diagnosticos) {
        super(String.join(System.lineSeparator(), diagnosticos.stream().map(Diagnostico::formatar).toList()));
        this.diagnosticos = List.copyOf(diagnosticos);
    }

    public List<Diagnostico> diagnosticos() {
        return diagnosticos;
    }
}

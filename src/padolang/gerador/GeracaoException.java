package padolang.gerador;

/** Erro na geração de código (situação que a análise semântica ainda vai barrar antes). */
public class GeracaoException extends RuntimeException {
    public GeracaoException(String mensagem, int linha) {
        super(String.format("Erro (linha %d): %s", linha, mensagem));
    }
}

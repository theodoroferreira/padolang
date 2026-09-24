package padolang.lexer;

/** Erro léxico: símbolo ou lexema que não pertence a nenhum padrão de token. */
public class LexicoException extends RuntimeException {
    private final int linha;
    private final int coluna;

    public LexicoException(String mensagem, int linha, int coluna) {
        super(String.format("padoERRO léxico (linha %d, coluna %d): %s", linha, coluna, mensagem));
        this.linha = linha;
        this.coluna = coluna;
    }

    public int getLinha() { return linha; }
    public int getColuna() { return coluna; }
}

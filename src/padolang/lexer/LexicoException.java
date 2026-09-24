package padolang.lexer;

/** Erro léxico: símbolo ou lexema que não pertence a nenhum padrão de token. */
public class LexicoException extends RuntimeException {

    public LexicoException(String mensagem, int linha, int coluna) {
        super(String.format("padoERRO léxico (linha %d, coluna %d): %s", linha, coluna, mensagem));
    }
}

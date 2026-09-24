package padolang.lsp.json;

/** JSON malformado na leitura, ou valor sem representação JSON na escrita. */
public class JsonException extends RuntimeException {

    public JsonException(String mensagem) {
        super(mensagem);
    }
}

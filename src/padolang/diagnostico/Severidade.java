package padolang.diagnostico;

/** ERRO impede a geração de código; AVISO só informa. */
public enum Severidade {
    ERRO("padoERRO"), AVISO("padoAVISO");

    /** Prefixo da mensagem. */
    public final String prefixo;

    Severidade(String prefixo) {
        this.prefixo = prefixo;
    }
}

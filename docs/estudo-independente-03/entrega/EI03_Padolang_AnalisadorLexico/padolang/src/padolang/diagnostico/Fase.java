package padolang.diagnostico;

/** Etapa do front-end em que o problema foi encontrado. */
public enum Fase {
    LEXICO("léxico"), SINTATICO("sintático"), SEMANTICO("semântico");

    /** Como a fase aparece na mensagem ("padoERRO léxico ..."). */
    public final String nome;

    Fase(String nome) {
        this.nome = nome;
    }
}

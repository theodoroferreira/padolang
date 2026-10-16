package padolang.ast;

/** Operadores unários: menos (-x) e negação lógica (padNOT x). */
public enum OperadorUnario {
    NEGATIVO("-"), NAO("padNOT");

    public final String simbolo;

    OperadorUnario(String simbolo) {
        this.simbolo = simbolo;
    }
}

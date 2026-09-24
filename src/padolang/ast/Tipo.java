package padolang.ast;

/** Tipos primitivos da Padolang. */
public enum Tipo {
    INTER, FLOAT, OLEAN, STRING;

    /** Nome do tipo como aparece no fonte (padoINTER, padoFLOAT...), usado nas mensagens de erro. */
    public String palavra() {
        return "pado" + name();
    }

    public boolean numerico() {
        return this == INTER || this == FLOAT;
    }
}

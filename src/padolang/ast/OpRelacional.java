package padolang.ast;

public enum OpRelacional {
    IGUAL("=="), DIFERENTE("!="), MENOR("<"), MENOR_IGUAL("<="), MAIOR(">"), MAIOR_IGUAL(">=");

    public final String simbolo;

    OpRelacional(String simbolo) {
        this.simbolo = simbolo;
    }
}

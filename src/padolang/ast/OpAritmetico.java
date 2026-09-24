package padolang.ast;

public enum OpAritmetico {
    SOMA("+"), SUBTRACAO("-"), MULTIPLICACAO("*"), DIVISAO("/");

    public final String simbolo;

    OpAritmetico(String simbolo) {
        this.simbolo = simbolo;
    }
}

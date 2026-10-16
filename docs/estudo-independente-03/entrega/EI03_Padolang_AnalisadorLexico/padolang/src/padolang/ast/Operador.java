package padolang.ast;

/**
 * Operadores binários, do mais fraco para o mais forte (mesma precedência do Java e do C).
 * A categoria diz que tipos o operador aceita; a análise semântica usa isso para escolher a regra.
 */
public enum Operador {
    OU("padOR", Categoria.LOGICO),
    E("padAND", Categoria.LOGICO),
    IGUAL("==", Categoria.IGUALDADE),
    DIFERENTE("!=", Categoria.IGUALDADE),
    MENOR("<", Categoria.RELACIONAL),
    MENOR_IGUAL("<=", Categoria.RELACIONAL),
    MAIOR(">", Categoria.RELACIONAL),
    MAIOR_IGUAL(">=", Categoria.RELACIONAL),
    SOMA("+", Categoria.ARITMETICO),
    SUBTRACAO("-", Categoria.ARITMETICO),
    MULTIPLICACAO("*", Categoria.ARITMETICO),
    DIVISAO("/", Categoria.ARITMETICO),
    MODULO("padoMOD", Categoria.ARITMETICO);

    public enum Categoria { LOGICO, IGUALDADE, RELACIONAL, ARITMETICO }

    /** Como o operador é escrito na padolang (usado em mensagens e no --ast). */
    public final String simbolo;
    public final Categoria categoria;

    Operador(String simbolo, Categoria categoria) {
        this.simbolo = simbolo;
        this.categoria = categoria;
    }
}

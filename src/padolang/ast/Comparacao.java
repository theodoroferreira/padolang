package padolang.ast;

/** <ExprLogica> ::= <Expressao> <OpRel> <Expressao> */
public record Comparacao(Expressao esquerda, OpRelacional operador, Expressao direita, int linha) {
}

package padolang.ast;

import java.util.List;

/**
 * Nós de expressão. Não existe nó para parênteses: o agrupamento já está
 * representado pela própria forma da árvore.
 */
public sealed interface Expressao {

    int linha();

    record Binaria(Expressao esquerda, OpAritmetico operador, Expressao direita, int linha) implements Expressao {
    }

    /** menos unário: -x */
    record Negacao(Expressao operando, int linha) implements Expressao {
    }

    record Variavel(String nome, int linha) implements Expressao {
    }

    record Chamada(String nome, List<Expressao> argumentos, int linha) implements Expressao {
    }

    /** O lexema é mantido como texto; conversão e checagem de faixa ficam para depois. */
    record LiteralInteiro(String lexema, int linha) implements Expressao {
    }

    record LiteralReal(String lexema, int linha) implements Expressao {
    }

    /** Lexema com aspas e escapes, já compatível com C. */
    record LiteralTexto(String lexema, int linha) implements Expressao {
    }

    record LiteralLogico(boolean valor, int linha) implements Expressao {
    }
}

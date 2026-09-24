package padolang.ast;

import java.util.List;

/**
 * Nós de expressão. Comparações e operações lógicas também são expressões (de tipo padoOLEAN),
 * como no Java. Não existe nó para parênteses: o agrupamento já está na forma da árvore.
 *
 * Todo nó guarda a posição (linha e coluna) do token que o representa: o operador, no caso de
 * Binaria e Unaria; o nome, em Variavel e Chamada; o próprio literal nos demais.
 */
public sealed interface Expressao {

    int linha();

    int coluna();

    record Binaria(Expressao esquerda, Operador operador, Expressao direita,
                   int linha, int coluna) implements Expressao {
    }

    /** -x  ou  padNOT x */
    record Unaria(OperadorUnario operador, Expressao operando, int linha, int coluna) implements Expressao {
    }

    record Variavel(String nome, int linha, int coluna) implements Expressao {
    }

    record Chamada(String nome, List<Expressao> argumentos, int linha, int coluna) implements Expressao {
    }

    /** padoINTER(x): conversão explícita para inteiro, truncando em direção a zero. */
    record ConversaoInter(Expressao valor, int linha, int coluna) implements Expressao {
    }

    /** O lexema é mantido como texto; a checagem de faixa é feita na análise semântica (R10). */
    record LiteralInteiro(String lexema, int linha, int coluna) implements Expressao {
    }

    record LiteralReal(String lexema, int linha, int coluna) implements Expressao {
    }

    /** Lexema com aspas e escapes, já compatível com C. */
    record LiteralTexto(String lexema, int linha, int coluna) implements Expressao {
    }

    record LiteralLogico(boolean valor, int linha, int coluna) implements Expressao {
    }
}

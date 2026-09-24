package padolang.semantica;

import padolang.ast.Comando;
import padolang.ast.Expressao;
import padolang.ast.Tipo;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * O que a análise semântica descobriu e o gerador precisa saber: o tipo de cada expressão
 * (para escolher printf, strcmp, divisão inteira, concatenação...) e o tipo da variável de
 * cada padoIN. Assim o gerador não precisa refazer a inferência de tipos nem manter escopos.
 *
 * Os mapas são por IDENTIDADE: dois nós iguais em conteúdo (o mesmo "x" na mesma linha, por
 * exemplo) continuam sendo nós diferentes da árvore.
 */
public final class Anotacoes {

    private final Map<Expressao, Tipo> tiposExpressoes = new IdentityHashMap<>();
    private final Map<Comando.Leitura, Tipo> tiposLeituras = new IdentityHashMap<>();

    void registrar(Expressao e, Tipo t) {
        tiposExpressoes.put(e, t);
    }

    void registrar(Comando.Leitura l, Tipo t) {
        tiposLeituras.put(l, t);
    }

    public Tipo tipo(Expressao e) {
        return exigir(tiposExpressoes.get(e), e);
    }

    public Tipo tipo(Comando.Leitura l) {
        return exigir(tiposLeituras.get(l), l);
    }

    private static Tipo exigir(Tipo t, Object no) {
        if (t == null) {
            throw new IllegalStateException("erro interno: nó sem tipo anotado pela semântica: " + no);
        }
        return t;
    }
}

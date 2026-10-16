package padolang.semantica;

import padolang.ast.Tipo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Tabela de símbolos das VARIÁVEIS, organizada como uma pilha de escopos.
 *
 *   - abrir um bloco { }  -> empilha um escopo novo;
 *   - fechar o bloco      -> desempilha, e as variáveis dele deixam de existir;
 *   - buscar um nome      -> procura do escopo mais interno para o mais externo.
 *
 * Cada função começa com uma pilha vazia (o primeiro escopo guarda os parâmetros), e o PADOIN
 * também. Por isso uma função nunca enxerga as variáveis do PADOIN: elas estão em outra pilha.
 * As funções ficam numa tabela separada, no AnalisadorSemantico.
 */
public final class TabelaDeSimbolos {

    /** Uma variável (ou parâmetro) declarada. */
    public record Simbolo(String nome, Tipo tipo, int linha, boolean parametro) {
    }

    private final Deque<Map<String, Simbolo>> escopos = new ArrayDeque<>();

    public void abrirEscopo() {
        escopos.push(new HashMap<>());
    }

    public void fecharEscopo() {
        escopos.pop();
    }

    public void declarar(Simbolo s) {
        escopos.peek().put(s.nome(), s);
    }

    /** Procura o nome em todos os escopos visíveis, do mais interno para o mais externo. */
    public Simbolo buscar(String nome) {
        for (Map<String, Simbolo> escopo : escopos) {   // ArrayDeque itera a partir do topo
            Simbolo s = escopo.get(nome);
            if (s != null) {
                return s;
            }
        }
        return null;
    }
}

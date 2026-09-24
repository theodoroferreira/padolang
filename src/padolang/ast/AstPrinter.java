package padolang.ast;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Imprime a AST como árvore (opção --ast).
 * Comandos viram nós da árvore; expressões são escritas em uma linha,
 * totalmente parentizadas, para deixar visível a precedência que o parser montou:
 * 2 + 3 * 4  aparece como  (2 + (3 * 4)),  e  a padOR b padAND c  como  (a padOR (b padAND c)).
 */
public final class AstPrinter {

    private final StringBuilder sb = new StringBuilder();

    public static String imprimir(Programa p) {
        AstPrinter printer = new AstPrinter();
        printer.sb.append("Programa\n");

        for (Funcao f : p.funcoes()) {
            printer.linha("", false, descreverFuncao(f));
            printer.comandos(f.corpo(), "│  ");
        }
        printer.linha("", true, "Principal (PADOIN)");
        printer.comandos(p.principal(), "   ");
        return printer.sb.toString();
    }

    private static String descreverFuncao(Funcao f) {
        String params = f.parametros().stream()
                .map(par -> par.tipo().palavra() + " " + par.nome())
                .collect(Collectors.joining(", "));
        return "Funcao " + f.nome() + "(" + params + ") : " + f.tipoRetorno().palavra();
    }

    private void comandos(List<Comando> lista, String prefixo) {
        for (int i = 0; i < lista.size(); i++) {
            comando(lista.get(i), prefixo, i == lista.size() - 1);
        }
    }

    private void comando(Comando c, String prefixo, boolean ultimo) {
        String filho = prefixo + (ultimo ? "   " : "│  ");
        switch (c) {
            case Comando.Declaracao d -> linha(prefixo, ultimo, "Declaracao(" + d.tipo().palavra() + ", "
                    + d.nome() + (d.inicial() != null ? ", " + expr(d.inicial()) : "") + ")");
            case Comando.Atribuicao a -> linha(prefixo, ultimo, resumo(a));
            case Comando.Incremento i -> linha(prefixo, ultimo, resumo(i));
            case Comando.ChamadaComando ch -> linha(prefixo, ultimo, "Chamada(" + expr(ch.chamada()) + ")");
            case Comando.Leitura l -> linha(prefixo, ultimo, "Leitura(" + l.nome() + ")");
            case Comando.Escrita e -> linha(prefixo, ultimo, (e.pulaLinha() ? "EscritaLinha(" : "Escrita(")
                    + (e.valor() == null ? "" : expr(e.valor())) + ")");
            case Comando.Retorno r -> linha(prefixo, ultimo, "Retorno(" + expr(r.valor()) + ")");
            case Comando.Interrompe i -> linha(prefixo, ultimo, "Interrompe (padBREAK)");
            case Comando.Prossegue p -> linha(prefixo, ultimo, "Prossegue (padCONTINUE)");
            case Comando.Se se -> {
                linha(prefixo, ultimo, "Se(" + expr(se.condicao()) + ")");
                boolean temSenao = se.senao() != null;
                linha(filho, !temSenao, "Entao");
                comandos(se.entao(), filho + (temSenao ? "│  " : "   "));
                if (temSenao) {
                    linha(filho, true, "Senao");
                    comandos(se.senao(), filho + "   ");
                }
            }
            case Comando.Repeticao rep -> {
                linha(prefixo, ultimo, "Repeticao(" + expr(rep.condicao()) + ")");
                linha(filho, false, "Inicio: " + (rep.inicio() == null ? "-" : resumo(rep.inicio())));
                linha(filho, false, "Passo:  " + (rep.passo() == null ? "-" : resumo(rep.passo())));
                linha(filho, true, "Corpo");
                comandos(rep.corpo(), filho + "   ");
            }
            case Comando.Enquanto enq -> {
                linha(prefixo, ultimo, "Enquanto(" + expr(enq.condicao()) + ")");
                comandos(enq.corpo(), filho);
            }
        }
    }

    /** Versão de uma linha para atribuição e ++/-- (também usada no início/passo do laço). */
    private static String resumo(Comando c) {
        return switch (c) {
            case Comando.Atribuicao a -> "Atrib(" + a.nome() + ", " + expr(a.valor()) + ")";
            case Comando.Incremento i -> (i.decremento() ? "Decremento(" : "Incremento(") + i.nome() + ")";
            default -> c.getClass().getSimpleName();
        };
    }

    private static String expr(Expressao e) {
        return switch (e) {
            case Expressao.Binaria b -> "(" + expr(b.esquerda()) + " " + b.operador().simbolo + " "
                    + expr(b.direita()) + ")";
            case Expressao.Unaria u -> "(" + u.operador().simbolo
                    + (u.operador() == OperadorUnario.NAO ? " " : "") + expr(u.operando()) + ")";
            case Expressao.Variavel v -> v.nome();
            case Expressao.Chamada ch -> ch.nome() + "(" + ch.argumentos().stream()
                    .map(AstPrinter::expr).collect(Collectors.joining(", ")) + ")";
            case Expressao.ConversaoInter c -> "padoINTER(" + expr(c.valor()) + ")";
            case Expressao.LiteralInteiro i -> i.lexema();
            case Expressao.LiteralReal r -> r.lexema();
            case Expressao.LiteralTexto t -> t.lexema();
            case Expressao.LiteralLogico l -> l.valor() ? "padoTRUE" : "padoFALSE";
        };
    }

    private void linha(String prefixo, boolean ultimo, String texto) {
        sb.append(prefixo).append(ultimo ? "└─ " : "├─ ").append(texto).append('\n');
    }
}

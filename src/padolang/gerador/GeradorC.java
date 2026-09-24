package padolang.gerador;

import padolang.ast.Comando;
import padolang.ast.Comparacao;
import padolang.ast.Expressao;
import padolang.ast.Funcao;
import padolang.ast.OpAritmetico;
import padolang.ast.Programa;
import padolang.ast.Tipo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gerador de código C: percorre a AST e emite um programa C equivalente.
 *
 * Mapeamento de tipos:
 *   padoINTER  -> int
 *   padoFLOAT  -> double
 *   padoOLEAN  -> bool    (stdbool.h)
 *   padoSTRING -> char *
 *
 * Todo identificador recebe o prefixo "pad_" no C. Assim nomes válidos na
 * Padolang, mas reservados em C (int, main, while, printf...), não quebram o código gerado.
 *
 * O gerador precisa saber o tipo de cada expressão para escolher o formato
 * do printf/scanf e para comparar textos com strcmp. Por isso ele mantém uma
 * pilha de escopos (nome -> tipo). Quando a análise semântica existir, ela
 * garante antes que todo nome existe e que os tipos batem; aqui o gerador só
 * CONSULTA os tipos.
 */
public class GeradorC {

    private static final String PREFIXO = "pad_";
    private static final String INDENTACAO = "    ";

    private final StringBuilder sb = new StringBuilder();
    private int nivel = 0;

    private final Deque<Map<String, Tipo>> escopos = new ArrayDeque<>();
    private final Map<String, Funcao> funcoes = new HashMap<>();
    private boolean usaLeituraTexto = false;

    public String gerar(Programa programa) {
        for (Funcao f : programa.funcoes()) {
            funcoes.put(f.nome(), f);
        }

        // O corpo é gerado primeiro, porque só depois de percorrê-lo sabemos se
        // a função auxiliar de leitura de texto é necessária no cabeçalho.
        // Protótipos: permitem chamar qualquer função, em qualquer ordem, inclusive recursão
        for (Funcao f : programa.funcoes()) {
            linha(assinatura(f) + ";");
        }
        if (!programa.funcoes().isEmpty()) {
            linha("");
        }

        for (Funcao f : programa.funcoes()) {
            funcao(f);
            linha("");
        }

        linha("int main(void) {");
        nivel++;
        escopos.push(new HashMap<>());
        comandos(programa.principal());
        escopos.pop();
        linha("return 0;");
        nivel--;
        linha("}");

        String corpo = sb.toString();
        sb.setLength(0);
        cabecalho();
        sb.append(corpo);
        return sb.toString();
    }

    // ------------------------------------------------------------ estrutura

    private void cabecalho() {
        linha("/* Gerado pelo transpilador Padolang */");
        linha("#include <stdio.h>");
        linha("#include <stdbool.h>");
        linha("#include <string.h>");
        linha("#include <stdlib.h>");
        linha("");
        if (usaLeituraTexto) {
            linha("/* Lê uma linha do teclado e devolve uma cópia alocada (padoIN de padoSTRING). */");
            linha("static char *pad_ler_texto(void) {");
            linha("    char buf[256];");
            linha("    if (scanf(\" %255[^\\n]\", buf) != 1) buf[0] = '\\0';");
            linha("    char *s = malloc(strlen(buf) + 1);");
            linha("    strcpy(s, buf);");
            linha("    return s;");
            linha("}");
            linha("");
        }
    }

    private String assinatura(Funcao f) {
        String params = f.parametros().isEmpty() ? "void" : f.parametros().stream()
                .map(p -> tipoC(p.tipo()) + " " + nome(p.nome()))
                .collect(Collectors.joining(", "));
        return tipoC(f.tipoRetorno()) + " " + nome(f.nome()) + "(" + params + ")";
    }

    private void funcao(Funcao f) {
        linha(assinatura(f) + " {");
        nivel++;
        Map<String, Tipo> escopo = new HashMap<>();
        for (Funcao.Parametro p : f.parametros()) {
            escopo.put(p.nome(), p.tipo());
        }
        escopos.push(escopo);
        comandos(f.corpo());
        escopos.pop();
        nivel--;
        linha("}");
    }

    /** Cada bloco { } da Padolang vira um bloco { } do C, com seu próprio escopo. */
    private void bloco(List<Comando> lista) {
        nivel++;
        escopos.push(new HashMap<>());
        comandos(lista);
        escopos.pop();
        nivel--;
    }

    private void comandos(List<Comando> lista) {
        for (Comando c : lista) {
            comando(c);
        }
    }

    // ------------------------------------------------------------ comandos

    private void comando(Comando c) {
        switch (c) {
            case Comando.Declaracao d -> {
                // Sem valor inicial, a variável começa com o "zero" do tipo (decisão da Padolang)
                String valor = d.inicial() != null ? expr(d.inicial()) : valorPadrao(d.tipo());
                linha(tipoC(d.tipo()) + " " + nome(d.nome()) + " = " + valor + ";");
                escopos.peek().put(d.nome(), d.tipo());
            }
            case Comando.Atribuicao a -> linha(simples(a) + ";");
            case Comando.Incremento i -> linha(simples(i) + ";");
            case Comando.Leitura l -> leitura(l);
            case Comando.Escrita e -> escrita(e);
            case Comando.Retorno r -> linha("return " + expr(r.valor()) + ";");
            case Comando.Se se -> condicional(se, false);
            case Comando.Repeticao rep -> {
                String ini = rep.inicio() == null ? "" : simples(rep.inicio());
                String passo = rep.passo() == null ? "" : simples(rep.passo());
                linha("for (" + ini + "; " + comparacao(rep.condicao()) + "; " + passo + ") {");
                bloco(rep.corpo());
                linha("}");
            }
        }
    }

    /** Atribuição e ++/-- sem o ';' — reaproveitado no cabeçalho do for. */
    private String simples(Comando c) {
        tipoDaVariavel(nomeAlvo(c), c.linha());   // garante que a variável existe
        return switch (c) {
            case Comando.Atribuicao a -> nome(a.nome()) + " = " + expr(a.valor());
            case Comando.Incremento i -> nome(i.nome()) + (i.decremento() ? "--" : "++");
            default -> throw new IllegalStateException("comando inesperado no for: " + c);
        };
    }

    private static String nomeAlvo(Comando c) {
        return switch (c) {
            case Comando.Atribuicao a -> a.nome();
            case Comando.Incremento i -> i.nome();
            default -> throw new IllegalStateException("comando inesperado: " + c);
        };
    }

    /** pad/oin. Um "oin pad" vira "else if" no C. */
    private void condicional(Comando.Se se, boolean encadeado) {
        String abertura = "if (" + comparacao(se.condicao()) + ") {";
        if (encadeado) {
            sb.append(abertura).append('\n');   // continua a linha "} else "
        } else {
            linha(abertura);
        }
        bloco(se.entao());

        List<Comando> senao = se.senao();
        if (senao == null) {
            linha("}");
        } else if (senao.size() == 1 && senao.get(0) instanceof Comando.Se proximo) {
            indentar();
            sb.append("} else ");
            condicional(proximo, true);
        } else {
            linha("} else {");
            bloco(senao);
            linha("}");
        }
    }

    /** padoPRINT: o formato depende do TIPO da expressão, não do texto. */
    private void escrita(Comando.Escrita e) {
        String valor = expr(e.valor());
        switch (tipoDe(e.valor())) {
            case INTER -> linha("printf(\"%d\", " + valor + ");");
            case FLOAT -> linha("printf(\"%g\", " + valor + ");");
            case STRING -> linha("printf(\"%s\", " + valor + ");");
            case OLEAN -> linha("printf(\"%s\", (" + valor + ") ? \"padoTRUE\" : \"padoFALSE\");");
        }
    }

    /** padoIN: scanf com o formato do tipo da variável. */
    private void leitura(Comando.Leitura l) {
        String v = nome(l.nome());
        switch (tipoDaVariavel(l.nome(), l.linha())) {
            case INTER -> linha("scanf(\"%d\", &" + v + ");");
            case FLOAT -> linha("scanf(\"%lf\", &" + v + ");");
            case OLEAN -> linha("{ int pad_tmp = 0; scanf(\"%d\", &pad_tmp); " + v + " = pad_tmp != 0; }");
            case STRING -> {
                usaLeituraTexto = true;
                linha(v + " = pad_ler_texto();");
            }
        }
    }

    // ------------------------------------------------------------ expressões

    private String comparacao(Comparacao c) {
        String esq = expr(c.esquerda());
        String dir = expr(c.direita());
        String op = c.operador().simbolo;
        // Em C, == entre char* compara endereços. Textos são comparados com strcmp.
        if (tipoDe(c.esquerda()) == Tipo.STRING && tipoDe(c.direita()) == Tipo.STRING) {
            return "strcmp(" + esq + ", " + dir + ") " + op + " 0";
        }
        return esq + " " + op + " " + dir;
    }

    private String expr(Expressao e) {
        return switch (e) {
            case Expressao.Binaria b -> {
                int p = precedencia(b);
                // filho da esquerda: parênteses se tiver precedência menor
                String esq = parentizar(b.esquerda(), precedencia(b.esquerda()) < p);
                // filho da direita: também com precedência igual, senão a - (b - c) viraria a - b - c
                String dir = parentizar(b.direita(), precedencia(b.direita()) <= p);
                yield esq + " " + b.operador().simbolo + " " + dir;
            }
            // Sempre parentizamos o operando não atômico: - -1 viraria "--1", que em C é decremento
            case Expressao.Negacao n -> "-" + parentizar(n.operando(), !(atomica(n.operando())));
            case Expressao.Variavel v -> {
                tipoDaVariavel(v.nome(), v.linha());   // garante que existe
                yield nome(v.nome());
            }
            case Expressao.Chamada ch -> nome(ch.nome()) + "(" + ch.argumentos().stream()
                    .map(this::expr).collect(Collectors.joining(", ")) + ")";
            case Expressao.LiteralInteiro i -> i.lexema();
            case Expressao.LiteralReal r -> r.lexema();
            case Expressao.LiteralTexto t -> t.lexema();     // escapes da Padolang já são os do C
            case Expressao.LiteralLogico l -> l.valor() ? "true" : "false";
        };
    }

    private String parentizar(Expressao e, boolean precisa) {
        String s = expr(e);
        return precisa ? "(" + s + ")" : s;
    }

    private static int precedencia(Expressao e) {
        if (e instanceof Expressao.Binaria b) {
            return (b.operador() == OpAritmetico.SOMA || b.operador() == OpAritmetico.SUBTRACAO) ? 1 : 2;
        }
        return e instanceof Expressao.Negacao ? 3 : 4;
    }

    private static boolean atomica(Expressao e) {
        return !(e instanceof Expressao.Binaria) && !(e instanceof Expressao.Negacao);
    }

    // ------------------------------------------------------------ tipos

    /** Tipo de uma expressão (inter + float = float, inter + inter = inter...). */
    private Tipo tipoDe(Expressao e) {
        return switch (e) {
            case Expressao.LiteralInteiro i -> Tipo.INTER;
            case Expressao.LiteralReal r -> Tipo.FLOAT;
            case Expressao.LiteralTexto t -> Tipo.STRING;
            case Expressao.LiteralLogico l -> Tipo.OLEAN;
            case Expressao.Variavel v -> tipoDaVariavel(v.nome(), v.linha());
            case Expressao.Negacao n -> tipoDe(n.operando());
            case Expressao.Chamada ch -> {
                Funcao f = funcoes.get(ch.nome());
                if (f == null) {
                    throw new GeracaoException("função '" + ch.nome() + "' não declarada", ch.linha());
                }
                yield f.tipoRetorno();
            }
            case Expressao.Binaria b -> {
                Tipo esq = tipoDe(b.esquerda());
                Tipo dir = tipoDe(b.direita());
                if (esq == Tipo.INTER && dir == Tipo.INTER) {
                    yield Tipo.INTER;
                }
                if ((esq == Tipo.INTER || esq == Tipo.FLOAT) && (dir == Tipo.INTER || dir == Tipo.FLOAT)) {
                    yield Tipo.FLOAT;
                }
                throw new GeracaoException("operação '" + b.operador().simbolo + "' entre " + esq + " e "
                        + dir + " não é suportada", b.linha());
            }
        };
    }

    private Tipo tipoDaVariavel(String nome, int linha) {
        for (Map<String, Tipo> escopo : escopos) {    // do escopo mais interno para o mais externo
            Tipo t = escopo.get(nome);
            if (t != null) {
                return t;
            }
        }
        throw new GeracaoException("variável '" + nome + "' não declarada", linha);
    }

    private static String tipoC(Tipo t) {
        return switch (t) {
            case INTER -> "int";
            case FLOAT -> "double";
            case OLEAN -> "bool";
            case STRING -> "char *";
        };
    }

    private static String valorPadrao(Tipo t) {
        return switch (t) {
            case INTER -> "0";
            case FLOAT -> "0.0";
            case OLEAN -> "false";
            case STRING -> "\"\"";
        };
    }

    private static String nome(String id) {
        return PREFIXO + id;
    }

    // ------------------------------------------------------------ saída

    private void linha(String texto) {
        if (!texto.isEmpty()) {
            indentar();
        }
        sb.append(texto).append('\n');
    }

    private void indentar() {
        sb.append(INDENTACAO.repeat(nivel));
    }
}

package padolang.gerador;

import padolang.ast.Comando;
import padolang.ast.Expressao;
import padolang.ast.Funcao;
import padolang.ast.Operador;
import padolang.ast.OperadorUnario;
import padolang.ast.Programa;
import padolang.ast.Tipo;
import padolang.semantica.Anotacoes;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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
 * padolang, mas reservados em C (int, main, while, printf...), não quebram o código gerado.
 * As funções auxiliares (ver Auxiliar) usam o prefixo "padolang_", que nunca colide com eles.
 *
 * O tipo de cada expressão vem das Anotacoes da análise semântica. O gerador parte do princípio
 * de que o programa está correto; uma IllegalStateException aqui indica defeito do transpilador,
 * nunca erro do programa padolang.
 *
 * O C gerado deve ser compilado com -fwrapv, para o estouro de inteiro dar a volta como no Java.
 */
public class GeradorC {

    private static final String PREFIXO = "pad_";
    private static final String INDENTACAO = "    ";

    // Precedência dos operadores do C, da mais fraca para a mais forte.
    private static final int P_OU = 1, P_E = 2, P_IGUALDADE = 3, P_RELACIONAL = 4,
            P_ADITIVO = 5, P_MULTIPLICATIVO = 6, P_UNARIO = 7, P_ATOMO = 8;

    /** Um trecho de expressão C e a precedência do seu operador principal. */
    private record Codigo(String texto, int precedencia) {
    }

    private final Anotacoes tipos;
    private final StringBuilder sb = new StringBuilder();
    private int nivel = 0;
    private final Set<Auxiliar> auxiliares = EnumSet.noneOf(Auxiliar.class);

    public GeradorC(Anotacoes tipos) {
        this.tipos = tipos;
    }

    public String gerar(Programa programa) {
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
        comandos(programa.principal());
        linha("return 0;");
        nivel--;
        linha("}");

        // O corpo é gerado primeiro porque só depois de percorrê-lo sabemos quais funções
        // auxiliares o programa usa; elas vão no cabeçalho, antes do corpo.
        String corpo = sb.toString();
        sb.setLength(0);
        cabecalho();
        sb.append(corpo);
        return sb.toString();
    }

    // ------------------------------------------------------------ estrutura

    private void cabecalho() {
        linha("/* Gerado pelo transpilador padolang. Compile com: gcc -std=c11 -fwrapv arquivo.c */");
        for (String h : List.of("stdio.h", "stdlib.h", "string.h", "stdbool.h", "math.h", "limits.h", "errno.h")) {
            linha("#include <" + h + ">");
        }
        linha("");
        for (Auxiliar a : auxiliares) {           // EnumSet itera na ordem do enum
            sb.append(a.codigo).append('\n');
        }
    }

    private void usar(Auxiliar a) {
        if (auxiliares.add(a)) {
            a.dependencias.forEach(this::usar);
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
        bloco(f.corpo());
        linha("}");
    }

    /** Cada bloco { } da padolang vira um bloco { } do C, com seu próprio escopo. */
    private void bloco(List<Comando> lista) {
        nivel++;
        comandos(lista);
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
                // Sem valor inicial, a variável começa com o "zero" do tipo (decisão da padolang)
                String valor = d.inicial() != null ? expr(d.inicial()).texto() : valorPadrao(d.tipo());
                linha(tipoC(d.tipo()) + " " + nome(d.nome()) + " = " + valor + ";");
            }
            case Comando.Atribuicao a -> linha(simples(a) + ";");
            case Comando.Incremento i -> linha(simples(i) + ";");
            case Comando.ChamadaComando ch -> linha(expr(ch.chamada()).texto() + ";");
            case Comando.Leitura l -> leitura(l);
            case Comando.Escrita e -> escrita(e);
            case Comando.Retorno r -> linha("return " + expr(r.valor()).texto() + ";");
            case Comando.Interrompe i -> linha("break;");
            case Comando.Prossegue p -> linha("continue;");
            case Comando.Se se -> condicional(se, false);
            case Comando.Repeticao rep -> {
                String ini = rep.inicio() == null ? "" : simples(rep.inicio());
                String passo = rep.passo() == null ? "" : simples(rep.passo());
                linha("for (" + ini + "; " + expr(rep.condicao()).texto() + "; " + passo + ") {");
                bloco(rep.corpo());
                linha("}");
            }
            case Comando.Enquanto enq -> {
                linha("while (" + expr(enq.condicao()).texto() + ") {");
                bloco(enq.corpo());
                linha("}");
            }
        }
    }

    /** Atribuição e ++/-- sem o ';', reaproveitado no cabeçalho do for. */
    private String simples(Comando c) {
        return switch (c) {
            case Comando.Atribuicao a -> nome(a.nome()) + " = " + expr(a.valor()).texto();
            case Comando.Incremento i -> nome(i.nome()) + (i.decremento() ? "--" : "++");
            default -> throw new IllegalStateException("comando inesperado no for: " + c);
        };
    }

    /** pad/oin. Um "oin pad" vira "else if" no C. */
    private void condicional(Comando.Se se, boolean encadeado) {
        String abertura = "if (" + expr(se.condicao()).texto() + ") {";
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

    /** padoPRINT / padoPRINTLN: o formato depende do TIPO da expressão. */
    private void escrita(Comando.Escrita e) {
        String nl = e.pulaLinha() ? "\\n" : "";
        if (e.valor() == null) {                              // padoPRINTLN()
            linha("putchar('\\n');");
            return;
        }
        String valor = expr(e.valor()).texto();
        switch (tipos.tipo(e.valor())) {
            case INTER -> linha("printf(\"%d" + nl + "\", " + valor + ");");
            case STRING -> linha("printf(\"%s" + nl + "\", " + valor + ");");
            case OLEAN -> linha("printf(\"%s" + nl + "\", " + valor + " ? \"padoTRUE\" : \"padoFALSE\");");
            case FLOAT -> {
                usar(Auxiliar.IMPRIMIR_REAL);
                linha("padolang_imprimir_real(" + valor + ");");
                if (e.pulaLinha()) {
                    linha("putchar('\\n');");
                }
            }
        }
    }

    /** padoIN: uma linha da entrada, convertida para o tipo da variável (erro de execução se inválida). */
    private void leitura(Comando.Leitura l) {
        Auxiliar aux = switch (tipos.tipo(l)) {
            case INTER -> Auxiliar.LER_INTER;
            case FLOAT -> Auxiliar.LER_REAL;
            case OLEAN -> Auxiliar.LER_OLEAN;
            case STRING -> Auxiliar.LER_LINHA;
        };
        usar(aux);
        String funcao = aux.name().toLowerCase();
        linha(nome(l.nome()) + " = padolang_" + funcao + "(" + l.linha() + ");");
    }

    // ------------------------------------------------------------ expressões

    private Codigo expr(Expressao e) {
        return switch (e) {
            case Expressao.Binaria b -> binaria(b);
            case Expressao.Unaria u -> {
                Codigo op = expr(u.operando());
                String simbolo = u.operador() == OperadorUnario.NAO ? "!" : "-";
                // parênteses se o operando tiver precedência menor, ou se começar com '-':
                // "- -1" viraria "--1", que em C é decremento
                boolean parenteses = op.precedencia() < P_UNARIO
                        || (simbolo.equals("-") && op.texto().startsWith("-"));
                yield new Codigo(simbolo + (parenteses ? "(" + op.texto() + ")" : op.texto()), P_UNARIO);
            }
            case Expressao.Variavel v -> atomo(nome(v.nome()));
            case Expressao.Chamada ch -> atomo(nome(ch.nome()) + "(" + ch.argumentos().stream()
                    .map(a -> expr(a).texto()).collect(Collectors.joining(", ")) + ")");
            case Expressao.ConversaoInter c -> {
                Codigo valor = expr(c.valor());
                if (tipos.tipo(c.valor()) == Tipo.INTER) {
                    yield valor;                          // padoINTER(inteiro) não muda nada
                }
                usar(Auxiliar.PARA_INTER);
                yield atomo("padolang_para_inter(" + valor.texto() + ", " + c.linha() + ")");
            }
            // Normaliza o número: em C, "010" seria octal (vale 8), e "08" nem compila
            case Expressao.LiteralInteiro i -> atomo(new BigInteger(i.lexema()).toString());
            case Expressao.LiteralReal r -> atomo(r.lexema());
            // Os escapes da padolang já são os do C. O '?' vira "\?" para o gcc não ler
            // trigrafias como "??!" (que viraria '|') no modo -std=c11.
            case Expressao.LiteralTexto t -> atomo(t.lexema().replace("?", "\\?"));
            case Expressao.LiteralLogico l -> atomo(l.valor() ? "true" : "false");
        };
    }

    private Codigo binaria(Expressao.Binaria b) {
        Operador op = b.operador();
        Tipo tEsq = tipos.tipo(b.esquerda());
        Tipo tDir = tipos.tipo(b.direita());

        // Concatenação: o lado que não é texto é convertido, como no Java
        if (op == Operador.SOMA && tipos.tipo(b) == Tipo.STRING) {
            usar(Auxiliar.CONCATENAR);
            return atomo("padolang_concatenar(" + comoTexto(b.esquerda()) + ", " + comoTexto(b.direita()) + ")");
        }

        // Textos: == entre char* compararia endereços; strcmp compara o conteúdo
        if (tEsq == Tipo.STRING && tDir == Tipo.STRING) {
            int p = op.categoria == Operador.Categoria.IGUALDADE ? P_IGUALDADE : P_RELACIONAL;
            return new Codigo("strcmp(" + expr(b.esquerda()).texto() + ", " + expr(b.direita()).texto() + ") "
                    + op.simbolo + " 0", p);
        }

        // Divisão e resto entre inteiros: com divisor literal (nunca 0, pela R14) o C resolve direto;
        // senão, a função auxiliar confere o zero na execução.
        boolean inteiros = tEsq == Tipo.INTER && tDir == Tipo.INTER;
        if (inteiros && (op == Operador.DIVISAO || op == Operador.MODULO)
                && !(b.direita() instanceof Expressao.LiteralInteiro)) {
            Auxiliar aux = op == Operador.DIVISAO ? Auxiliar.DIVIDIR : Auxiliar.RESTO;
            usar(aux);
            return atomo("padolang_" + aux.name().toLowerCase() + "(" + expr(b.esquerda()).texto() + ", "
                    + expr(b.direita()).texto() + ", " + b.linha() + ")");
        }

        int p = precedencia(op);
        String simboloC = switch (op) {
            case OU -> "||";
            case E -> "&&";
            case MODULO -> "%";
            default -> op.simbolo;
        };
        // filho da esquerda: parênteses se tiver precedência menor;
        // filho da direita: também com precedência igual, senão a - (b - c) viraria a - b - c
        String esq = parentizar(expr(b.esquerda()), p, false);
        String dir = parentizar(expr(b.direita()), p, true);
        return new Codigo(esq + " " + simboloC + " " + dir, p);
    }

    /**
     * Parênteses em volta de um operando de um operador binário de precedência pai.
     * Obrigatórios quando o filho tem precedência menor (ou igual, do lado direito: senão
     * a - (b - c) viraria a - b - c). Os demais só evitam os avisos -Wparentheses do gcc
     * ("a || b && c", "a < b == c < d", "!a == b"), sem mudar o significado.
     */
    private static String parentizar(Codigo filho, int pai, boolean direita) {
        int f = filho.precedencia();
        boolean obrigatorio = direita ? f <= pai : f < pai;
        boolean comparacao = pai == P_IGUALDADE || pai == P_RELACIONAL;
        boolean clareza = (pai == P_OU && f == P_E)
                || (comparacao && (f == P_IGUALDADE || f == P_RELACIONAL || filho.texto().startsWith("!")));
        return obrigatorio || clareza ? "(" + filho.texto() + ")" : filho.texto();
    }

    /** Converte o valor de uma expressão para char * (usado na concatenação). */
    private String comoTexto(Expressao e) {
        String c = expr(e).texto();
        return switch (tipos.tipo(e)) {
            case STRING -> c;
            case INTER -> {
                usar(Auxiliar.INTER_TEXTO);
                yield "padolang_inter_texto(" + c + ")";
            }
            case FLOAT -> {
                usar(Auxiliar.REAL_TEXTO);
                yield "padolang_real_texto(" + c + ")";
            }
            case OLEAN -> "(" + c + " ? \"padoTRUE\" : \"padoFALSE\")";
        };
    }

    private static int precedencia(Operador op) {
        return switch (op.categoria) {
            case LOGICO -> op == Operador.OU ? P_OU : P_E;
            case IGUALDADE -> P_IGUALDADE;
            case RELACIONAL -> P_RELACIONAL;
            case ARITMETICO -> (op == Operador.SOMA || op == Operador.SUBTRACAO) ? P_ADITIVO : P_MULTIPLICATIVO;
        };
    }

    private static Codigo atomo(String texto) {
        return new Codigo(texto, P_ATOMO);
    }

    // ------------------------------------------------------------ tipos

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

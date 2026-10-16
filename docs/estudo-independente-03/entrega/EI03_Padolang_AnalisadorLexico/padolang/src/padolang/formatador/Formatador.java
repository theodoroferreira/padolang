package padolang.formatador;

import padolang.lexer.Lexer;
import padolang.lexer.Token;
import padolang.lexer.TokenType;
import padolang.parser.Parser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static padolang.lexer.TokenType.*;

/**
 * Formatador da padolang (padolang fmt).
 *
 * Trabalha sobre a sequência de tokens do léxico no modo EDITOR, e não sobre a AST: a AST perde os
 * comentários e não diferencia "oin pad (...)" de "oin { pad (...) {...} }". Estilo:
 *
 *   - 2 espaços de indentação por '{'; '{' na mesma linha, depois de um espaço;
 *   - '}' sozinho na linha, exceto em "} oin {" e "} oin pad (...) {";
 *   - um comando por linha (quebra depois de cada 👍), com o 👍 colado ao token anterior;
 *   - um espaço em volta dos operadores binários e depois da vírgula; menos unário colado;
 *   - sem espaço entre o nome e o '(' em chamadas, padoPRINT, padoIN e padoINTER(...);
 *     um espaço entre pad, papapadoin ou padoinquanto e o '(';
 *   - comentário em linha própria fica na indentação do bloco; no fim da linha, depois de um espaço;
 *   - comentário de bloco (#* ... *#) mantém o texto original, inclusive as quebras de linha de
 *     dentro dele; no meio de uma linha, fica entre espaços e não quebra a linha;
 *   - no máximo uma linha em branco seguida, nenhuma no começo ou no fim de um bloco;
 *   - o arquivo termina com uma quebra de linha (CRLF se o original usava CRLF).
 *
 * O 👍 com tom de pele (👍🏽) mantém o texto original.
 *
 * Segurança: só formata um fonte que passa pelo léxico e pelo sintático (senão lança a
 * CompilacaoException correspondente), e confere no fim que o resultado tem os mesmos tokens
 * (tipo e lexema) e os mesmos comentários do original. Se não tiver, lança IllegalStateException
 * ("erro interno do formatador") em vez de devolver um programa com outro significado.
 */
public final class Formatador {

    private static final String INDENTACAO = "  ";
    private static final int BOM = 0xFEFF;

    /** Tokens depois dos quais um '-' é menos unário. */
    private static final Set<TokenType> ANTES_DE_UNARIO = EnumSet.of(
            MAIS, MENOS, VEZES, DIVIDIDO, MODULO, E, OU, NAO,
            ATRIB, IGUAL, DIFERENTE, MENOR, MENOR_IGUAL, MAIOR, MAIOR_IGUAL,
            ABRE_PAR, VIRGULA, RETORNO);

    /** Tokens colados ao '(' que vem depois: chamadas, escrita, leitura e a conversão padoINTER(...). */
    private static final Set<TokenType> COLADOS_AO_PARENTESE = EnumSet.of(
            ID, ESCRITA, ESCRITA_LINHA, LEITURA, TIPO_INTER, ABRE_PAR);

    private Formatador() {
    }

    /** Devolve o fonte formatado. Lança CompilacaoException se o fonte tiver erro léxico ou sintático. */
    public static String formatar(String fonte) {
        if (!fonte.isEmpty() && fonte.codePointAt(0) == BOM) {
            fonte = fonte.substring(Character.charCount(BOM));
        }
        new Parser(new Lexer(fonte).tokenizar()).programa();   // lança o padoERRO, se houver

        String quebra = fonte.contains("\r\n") ? "\r\n" : "\n";
        String resultado = String.join(quebra, new Formatacao(fonte).linhas()) + quebra;

        conferir(fonte, resultado);
        return resultado;
    }

    /** A checagem de segurança: mesmos tokens (sem posição) e mesmos comentários. */
    private static void conferir(String original, String formatado) {
        List<String> antes, depois;
        try {
            antes = assinatura(original);
            depois = assinatura(formatado);
        } catch (RuntimeException e) {
            throw new IllegalStateException("erro interno do formatador: o resultado não é mais válido ("
                    + e.getMessage() + ")", e);
        }
        if (!antes.equals(depois)) {
            int i = 0;
            while (i < Math.min(antes.size(), depois.size()) && antes.get(i).equals(depois.get(i))) {
                i++;
            }
            String esperado = i < antes.size() ? antes.get(i) : "<fim>";
            String obtido = i < depois.size() ? depois.get(i) : "<fim>";
            throw new IllegalStateException("erro interno do formatador: o token " + (i + 1)
                    + " mudaria de " + esperado + " para " + obtido);
        }
    }

    private static List<String> assinatura(String fonte) {
        List<String> tokens = new ArrayList<>();
        for (Token t : new Lexer(fonte).tokenizar()) {
            tokens.add(t.tipo() + " " + t.lexema());
        }
        for (Token t : new Lexer(fonte, Lexer.Modo.EDITOR).tokenizar()) {
            if (t.tipo() == COMENTARIO) {
                tokens.add(t.tipo() + " " + t.lexema().stripTrailing());
            }
        }
        return tokens;
    }

    // ------------------------------------------------------------------ a formatação em si

    /** Estado de uma formatação: percorre os tokens uma vez, montando as linhas de saída. */
    private static final class Formatacao {

        private final List<Token> tokens;
        private final int[][] linhasFonte;              // code points de cada linha do fonte
        private final List<String> saida = new ArrayList<>();
        private final StringBuilder linha = new StringBuilder();

        private int nivel = 0;                          // profundidade de chaves
        private boolean quebrar = false;                // o próximo token começa uma linha nova
        private Token anterior = null;                  // último token escrito (inclusive comentário)
        private Token anteriorCodigo = null;            // último token escrito, sem contar comentários
        private boolean anteriorEraUnario = false;      // o anteriorCodigo é um menos unário

        Formatacao(String fonte) {
            this.tokens = new Lexer(fonte, Lexer.Modo.EDITOR).tokenizar();
            // só '\n' quebra linha, como no léxico
            String[] linhas = fonte.split("\n", -1);
            this.linhasFonte = new int[linhas.length][];
            for (int i = 0; i < linhas.length; i++) {
                linhasFonte[i] = linhas[i].codePoints().toArray();
            }
        }

        List<String> linhas() {
            for (Token t : tokens) {
                if (t.tipo() == EOF) break;
                escrever(t);
            }
            fecharLinha();
            return saida;
        }

        private void escrever(Token t) {
            // comentário na mesma linha do token anterior: continua nela, depois de um espaço
            if (t.tipo() == COMENTARIO && anterior != null && t.linha() == ultimaLinha(anterior)) {
                if (!ehBloco(t)) {
                    linha.append(' ').append(texto(t));
                    quebrar = true;                     // o de linha vai até o fim dela
                } else {
                    // o de bloco não muda a quebra pendente (a de um 👍 antes dele, por exemplo)
                    if (ehBloco(anterior) || espaco(anteriorCodigo, anteriorEraUnario, t)) {
                        linha.append(' ');
                    }
                    linha.append(texto(t));
                }
                anterior = t;
                return;
            }

            if (t.tipo() == FECHA_CHAVE) {
                nivel--;
                quebrar = true;
            }
            if (t.tipo() == SENAO && anteriorCodigo != null && anteriorCodigo.tipo() == FECHA_CHAVE
                    && anterior == anteriorCodigo) {
                quebrar = false;                        // "} oin"
            }

            boolean unario = t.tipo() == MENOS && ehUnario();

            if (quebrar || linha.isEmpty()) {
                novaLinha(t);
                linha.append(INDENTACAO.repeat(Math.max(nivel, 0)));
            } else if (ehBloco(anterior) || espaco(anteriorCodigo, anteriorEraUnario, t)) {
                linha.append(' ');                      // depois de um comentário de bloco, sempre
            }
            linha.append(texto(t));

            quebrar = switch (t.tipo()) {
                case ABRE_CHAVE, FECHA_CHAVE, JOINHA, COMENTARIO -> true;
                default -> false;
            };
            if (t.tipo() == ABRE_CHAVE) {
                nivel++;
            }

            anterior = t;
            if (t.tipo() != COMENTARIO) {
                anteriorCodigo = t;
                anteriorEraUnario = unario;
            }
        }

        /** Fecha a linha atual (se houver) e decide se entra uma linha em branco antes de t. */
        private void novaLinha(Token t) {
            boolean primeira = saida.isEmpty() && linha.isEmpty();
            fecharLinha();
            if (primeira || anterior == null) return;

            boolean havia = t.linha() - ultimaLinha(anterior) >= 2;
            // "{" no fim da linha anterior, com ou sem comentário depois dele
            boolean inicioDeBloco = anteriorCodigo != null && anteriorCodigo.tipo() == ABRE_CHAVE
                    && anteriorCodigo.linha() == anterior.linha();
            boolean fimDeBloco = t.tipo() == FECHA_CHAVE;
            if (havia && !inicioDeBloco && !fimDeBloco) {
                saida.add("");
            }
        }

        private void fecharLinha() {
            if (!linha.isEmpty()) {
                saida.add(linha.toString().stripTrailing());
                linha.setLength(0);
            }
        }

        /** Um comentário de bloco: #* ... *# (o de linha é # ...). */
        private static boolean ehBloco(Token t) {
            return t != null && t.tipo() == COMENTARIO && t.lexema().startsWith("#*");
        }

        /** A linha em que o token termina: só é outra num comentário de bloco de várias linhas. */
        private static int ultimaLinha(Token t) {
            return t.linha() + (int) t.lexema().chars().filter(c -> c == '\n').count();
        }

        /** O '-' atual é unário se vier no início, depois de operador, '(', ',', '=' ou padOUT. */
        private boolean ehUnario() {
            return anteriorCodigo == null || ANTES_DE_UNARIO.contains(anteriorCodigo.tipo())
                    || (anteriorCodigo.tipo() == MENOS && anteriorEraUnario);
        }

        /** Se vai um espaço entre o token anterior a e o token b, na mesma linha. */
        private static boolean espaco(Token a, boolean aUnario, Token b) {
            if (a == null) return false;
            TokenType ta = a.tipo(), tb = b.tipo();

            if (tb == JOINHA || tb == VIRGULA || tb == FECHA_PAR) return false;
            if (tb == INCREMENTO || tb == DECREMENTO) return false;
            if (ta == ABRE_PAR) return false;
            if (ta == MENOS && aUnario) return tb == MENOS;     // "- -x": "--" viraria decremento
            if (tb == ABRE_PAR) return !COLADOS_AO_PARENTESE.contains(ta);
            return true;
        }

        /** O texto do token como está no fonte (preserva o tom de pele do 👍); o lexema nos demais. */
        private String texto(Token t) {
            if (t.tipo() == JOINHA) {
                int[] cps = linhasFonte[t.linha() - 1];
                return new String(cps, t.coluna() - 1, t.tamanho());
            }
            if (t.tipo() == COMENTARIO) {
                return t.lexema().stripTrailing();
            }
            return t.lexema();
        }
    }
}

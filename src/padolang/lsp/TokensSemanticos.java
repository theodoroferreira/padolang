package padolang.lsp;

import padolang.lexer.Lexer;
import padolang.lexer.Token;
import padolang.lexer.TokenType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static padolang.lexer.TokenType.*;

/**
 * Semantic Tokens do LSP (textDocument/semanticTokens/full): a cor de cada token, decidida pelo
 * servidor.
 *
 * Trabalha direto sobre os tokens do léxico no modo EDITOR, sem AST: assim funciona mesmo com erro
 * de sintaxe no meio do arquivo. O único dado "semântico" é saber se um identificador é parâmetro, e
 * isso sai sem resolver escopos: a R2 proíbe sombrear, então, dentro de uma função, um nome igual a
 * um parâmetro dela é sempre o parâmetro. Para saber em que função um token está basta contar as
 * chaves desde o padUNCTION.
 *
 * Codificação: 5 inteiros por token, [Δlinha, Δcaractere, tamanho, tipo, modificadores], em ordem de
 * posição. Δcaractere é relativo ao token anterior só quando os dois estão na mesma linha. Linha e
 * caractere estão em UTF-16 (ver Posicoes).
 */
public final class TokensSemanticos {

    /** A legenda, na ordem dos índices usados em data. São todos tipos padrão do LSP. */
    public static final List<String> TIPOS = List.of(
            "keyword", "type", "function", "parameter", "variable", "string", "number", "operator", "comment");
    public static final List<String> MODIFICADORES = List.of("declaration");

    private static final int KEYWORD = 0, TYPE = 1, FUNCTION = 2, PARAMETER = 3, VARIABLE = 4,
            STRING_ = 5, NUMBER = 6, OPERATOR = 7, COMMENT = 8;
    private static final int DECLARATION = 1;   // bit 0

    private static final Set<TokenType> PALAVRAS = EnumSet.of(
            PROGRAMA, DECLARA, SE, SENAO, REPETICAO, ENQUANTO, INTERROMPE, PROSSEGUE, FUNCAO, RETORNO,
            LEITURA, ESCRITA, ESCRITA_LINHA, VERDADEIRO, FALSO);
    private static final Set<TokenType> TIPOS_PADOLANG = EnumSet.of(TIPO_INTER, TIPO_FLOAT, TIPO_OLEAN, TIPO_STRING);
    private static final Set<TokenType> OPERADORES = EnumSet.of(
            E, OU, NAO, MODULO, MAIS, MENOS, VEZES, DIVIDIDO, INCREMENTO, DECREMENTO,
            ATRIB, IGUAL, DIFERENTE, MENOR, MENOR_IGUAL, MAIOR, MAIOR_IGUAL, JOINHA);

    private TokensSemanticos() {
    }

    /** O array data do resultado de semanticTokens/full. */
    public static int[] codificar(String fonte) {
        List<Token> tokens = new ArrayList<>();
        for (Token t : new Lexer(fonte, Lexer.Modo.EDITOR).tokenizar()) {
            if (t.tipo() != ERRO && t.tipo() != EOF) tokens.add(t);
        }
        Posicoes posicoes = new Posicoes(fonte);

        int[] data = new int[tokens.size() * 5];
        int n = 0;
        int linhaAnterior = 0, caractereAnterior = 0;

        Set<String> parametros = null;   // parâmetros da função em que estamos (null: fora de função)
        Set<String> cabecalho = null;    // parâmetros sendo lidos no cabeçalho de uma função
        int profundidade = 0;            // chaves abertas
        int profundidadeDaFuncao = -1;   // profundidade antes do '{' do corpo da função atual

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            Token antes = i > 0 ? tokens.get(i - 1) : null;
            Token antes2 = i > 1 ? tokens.get(i - 2) : null;
            Token depois = i + 1 < tokens.size() ? tokens.get(i + 1) : null;

            int tipo = -1, modificadores = 0;
            switch (t.tipo()) {
                case FUNCAO -> {
                    cabecalho = new HashSet<>();
                    tipo = KEYWORD;
                }
                case ABRE_CHAVE -> {
                    if (cabecalho != null) {                // começa o corpo da função
                        parametros = cabecalho;
                        cabecalho = null;
                        profundidadeDaFuncao = profundidade;
                    }
                    profundidade++;
                }
                case FECHA_CHAVE -> {
                    profundidade--;
                    if (parametros != null && profundidade <= profundidadeDaFuncao) {
                        parametros = null;                  // saiu da função
                        profundidadeDaFuncao = -1;
                    }
                }
                case ID -> {
                    boolean depoisDeTipo = antes != null && TIPOS_PADOLANG.contains(antes.tipo());
                    if (depoisDeTipo && antes2 != null && antes2.tipo() == FUNCAO) {
                        tipo = FUNCTION;                     // padUNCTION <Tipo> nome
                        modificadores = DECLARATION;
                    } else if (depois != null && depois.tipo() == ABRE_PAR) {
                        tipo = FUNCTION;                     // chamada
                    } else if (cabecalho != null && depoisDeTipo) {
                        tipo = PARAMETER;                    // (<Tipo> nome, ...)
                        modificadores = DECLARATION;
                        cabecalho.add(t.lexema());
                    } else if (parametros != null && parametros.contains(t.lexema())) {
                        tipo = PARAMETER;
                    } else {
                        tipo = VARIABLE;
                        if (depoisDeTipo && antes2 != null && antes2.tipo() == DECLARA) {
                            modificadores = DECLARATION;     // padoin <Tipo> nome
                        }
                    }
                }
                case NUM_INT, NUM_FLOAT -> tipo = NUMBER;
                case STRING -> tipo = STRING_;
                case COMENTARIO -> tipo = COMMENT;
                default -> {
                    if (PALAVRAS.contains(t.tipo())) tipo = KEYWORD;
                    else if (TIPOS_PADOLANG.contains(t.tipo())) tipo = TYPE;
                    else if (OPERADORES.contains(t.tipo())) tipo = OPERATOR;
                }
            }
            if (tipo < 0) continue;                          // ( ) , e { } ficam sem cor semântica

            int linha = t.linha() - 1;
            int caractere = posicoes.utf16(t.linha(), t.coluna());
            int tamanho = posicoes.tamanhoUtf16(t.linha(), t.coluna(), t.tamanho());
            data[n++] = linha - linhaAnterior;
            data[n++] = linha == linhaAnterior ? caractere - caractereAnterior : caractere;
            data[n++] = tamanho;
            data[n++] = tipo;
            data[n++] = modificadores;
            linhaAnterior = linha;
            caractereAnterior = caractere;
        }
        return java.util.Arrays.copyOf(data, n);
    }
}

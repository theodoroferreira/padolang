package padolang.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analisador léxico da Padolang, escrito à mão.
 *
 * O fonte é percorrido por code points Unicode (e não por char), porque o 👍
 * (U+1F44D) ocupa dois char em Java (par surrogate). Assim cada símbolo de Σ
 * corresponde a exatamente uma posição do vetor cp.
 */
public class Lexer {

    private static final int JOINHA = 0x1F44D;           // 👍
    private static final int TOM_PELE_INICIO = 0x1F3FB;  // 👍🏻 ... 👍🏿
    private static final int TOM_PELE_FIM = 0x1F3FF;
    private static final int SELETOR_VARIACAO = 0xFE0F;

    /** Palavras reservadas: o identificador é lido inteiro (maior casamento) e só depois consultado aqui. */
    private static final Map<String, TokenType> RESERVADAS = Map.ofEntries(
            Map.entry("PADOIN", TokenType.PROGRAMA),
            Map.entry("padoin", TokenType.DECLARA),
            Map.entry("padoINTER", TokenType.TIPO_INTER),
            Map.entry("padoFLOAT", TokenType.TIPO_FLOAT),
            Map.entry("padoOLEAN", TokenType.TIPO_OLEAN),
            Map.entry("padoSTRING", TokenType.TIPO_STRING),
            Map.entry("padoIN", TokenType.LEITURA),
            Map.entry("padoPRINT", TokenType.ESCRITA),
            Map.entry("pad", TokenType.SE),
            Map.entry("oin", TokenType.SENAO),
            Map.entry("papapadoin", TokenType.REPETICAO),
            Map.entry("padUNCTION", TokenType.FUNCAO),
            Map.entry("padOUT", TokenType.RETORNO),
            Map.entry("padoTRUE", TokenType.VERDADEIRO),
            Map.entry("padoFALSE", TokenType.FALSO)
    );

    private final int[] cp;
    private int pos = 0;
    private int linha = 1;
    private int coluna = 1;
    private final List<Token> tokens = new ArrayList<>();

    public Lexer(String fonte) {
        this.cp = fonte.codePoints().toArray();
    }

    public List<Token> tokenizar() {
        while (!fim()) {
            int c = atual();

            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                avancar();
                continue;
            }

            int l = linha, col = coluna; // posição de início do lexema

            if (c == '#') {
                comentario();
            } else if (ehLetra(c)) {
                identificadorOuReservada(l, col);
            } else if (ehDigito(c)) {
                numero(l, col);
            } else if (c == '"') {
                string(l, col);
            } else if (c == JOINHA) {
                joinha(l, col);
            } else {
                operadorOuDelimitador(l, col);
            }
        }
        tokens.add(new Token(TokenType.EOF, "<fim>", linha, coluna));
        return tokens;
    }

    // ---------------------------------------------------------------- padrões

    /** #[^\n]*  — comentário até o fim da linha, descartado. */
    private void comentario() {
        while (!fim() && atual() != '\n') {
            avancar();
        }
    }

    /** [a-zA-Z_][a-zA-Z0-9_]*  — depois consulta a tabela de reservadas. */
    private void identificadorOuReservada(int l, int col) {
        StringBuilder sb = new StringBuilder();
        while (!fim() && (ehLetra(atual()) || ehDigito(atual()))) {
            sb.appendCodePoint(avancar());
        }
        String lexema = sb.toString();
        emitir(RESERVADAS.getOrDefault(lexema, TokenType.ID), lexema, l, col);
    }

    /** [0-9]+ (NUM_INT)  ou  [0-9]+\.[0-9]+ (NUM_FLOAT). */
    private void numero(int l, int col) {
        StringBuilder sb = new StringBuilder();
        lerDigitos(sb);

        TokenType tipo = TokenType.NUM_INT;
        if (!fim() && atual() == '.') {
            sb.appendCodePoint(avancar());
            if (fim() || !ehDigito(atual())) {
                throw new LexicoException("número real malformado '" + sb + "' (esperado dígito após o ponto)", l, col);
            }
            lerDigitos(sb);
            tipo = TokenType.NUM_FLOAT;
        }

        if (!fim() && ehLetra(atual())) {
            throw new LexicoException("lexema inválido '" + sb + Character.toString(atual())
                    + "...' (identificadores não podem começar com dígito)", l, col);
        }
        emitir(tipo, sb.toString(), l, col);
    }

    /** "( [^"\\\n] | \\[nt"\\] )*"  — o lexema guarda aspas e escapes, compatíveis com C. */
    private void string(int l, int col) {
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(avancar()); // aspa de abertura

        while (true) {
            if (fim() || atual() == '\n') {
                throw new LexicoException("string não fechada", l, col);
            }
            int c = avancar();
            if (c == '"') {
                sb.append('"');
                break;
            }
            if (c == '\\') {
                if (fim() || "nt\"\\".indexOf(atual()) < 0) {
                    throw new LexicoException("sequência de escape inválida na string", linha, coluna - 1);
                }
                sb.append('\\').appendCodePoint(avancar());
            } else {
                sb.appendCodePoint(c);
            }
        }
        emitir(TokenType.STRING, sb.toString(), l, col);
    }

    /** 👍 com modificador de tom de pele e seletor de variação opcionais. */
    private void joinha(int l, int col) {
        avancar();
        if (!fim() && atual() >= TOM_PELE_INICIO && atual() <= TOM_PELE_FIM) {
            avancar();
        }
        if (!fim() && atual() == SELETOR_VARIACAO) {
            avancar();
        }
        emitir(TokenType.JOINHA, "👍", l, col);
    }

    /** Operadores com prefixo comum resolvidos por maior casamento: ++ antes de +, == antes de =, etc. */
    private void operadorOuDelimitador(int l, int col) {
        int c = avancar();
        switch (c) {
            case '+' -> emitir(casar('+') ? TokenType.INCREMENTO : TokenType.MAIS, l, col);
            case '-' -> emitir(casar('-') ? TokenType.DECREMENTO : TokenType.MENOS, l, col);
            case '*' -> emitir(TokenType.VEZES, l, col);
            case '/' -> emitir(TokenType.DIVIDIDO, l, col);
            case '=' -> emitir(casar('=') ? TokenType.IGUAL : TokenType.ATRIB, l, col);
            case '<' -> emitir(casar('=') ? TokenType.MENOR_IGUAL : TokenType.MENOR, l, col);
            case '>' -> emitir(casar('=') ? TokenType.MAIOR_IGUAL : TokenType.MAIOR, l, col);
            case '!' -> {
                if (!casar('=')) {
                    throw new LexicoException("'!' isolado não é um operador válido (use !=)", l, col);
                }
                emitir(TokenType.DIFERENTE, l, col);
            }
            case '(' -> emitir(TokenType.ABRE_PAR, l, col);
            case ')' -> emitir(TokenType.FECHA_PAR, l, col);
            case '{' -> emitir(TokenType.ABRE_CHAVE, l, col);
            case '}' -> emitir(TokenType.FECHA_CHAVE, l, col);
            case ',' -> emitir(TokenType.VIRGULA, l, col);
            default -> throw new LexicoException(
                    "caractere inválido '" + Character.toString(c) + "' (U+" + String.format("%04X", c) + ")", l, col);
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private boolean fim() { return pos >= cp.length; }

    private int atual() { return cp[pos]; }

    private int avancar() {
        int c = cp[pos++];
        if (c == '\n') {
            linha++;
            coluna = 1;
        } else {
            coluna++;
        }
        return c;
    }

    /** Consome o próximo símbolo se for o esperado (usado no maior casamento). */
    private boolean casar(int esperado) {
        if (!fim() && atual() == esperado) {
            avancar();
            return true;
        }
        return false;
    }

    private void lerDigitos(StringBuilder sb) {
        while (!fim() && ehDigito(atual())) {
            sb.appendCodePoint(avancar());
        }
    }

    /** Σ das letras: apenas ASCII. 'ç', 'ã' etc. fora de strings são erro léxico. */
    private static boolean ehLetra(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean ehDigito(int c) {
        return c >= '0' && c <= '9';
    }

    private void emitir(TokenType tipo, String lexema, int l, int col) {
        tokens.add(new Token(tipo, lexema, l, col));
    }

    /** Para operadores/delimitadores o lexema é recuperado do próprio fonte. */
    private void emitir(TokenType tipo, int l, int col) {
        StringBuilder sb = new StringBuilder();
        int tamanho = (tipo == TokenType.INCREMENTO || tipo == TokenType.DECREMENTO
                || tipo == TokenType.IGUAL || tipo == TokenType.DIFERENTE
                || tipo == TokenType.MENOR_IGUAL || tipo == TokenType.MAIOR_IGUAL) ? 2 : 1;
        for (int i = pos - tamanho; i < pos; i++) {
            sb.appendCodePoint(cp[i]);
        }
        emitir(tipo, sb.toString(), l, col);
    }
}

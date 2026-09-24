package padolang.lexer;

import padolang.diagnostico.Diagnostico;
import padolang.diagnostico.Fase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analisador léxico da Padolang, escrito à mão.
 *
 * O fonte é percorrido por code points Unicode (e não por char), porque o 👍
 * (U+1F44D) ocupa dois char em Java (par surrogate). Assim cada símbolo de Σ
 * corresponde a exatamente uma posição do vetor cp.
 *
 * Dois modos:
 *   - COMPILADOR (padrão): comentários são descartados e o primeiro erro lança LexicoException;
 *   - EDITOR (formatter e LSP): comentários viram tokens COMENTARIO e cada erro vira um token ERRO,
 *     com o diagnóstico guardado em erros(); a análise continua depois do trecho inválido.
 * Nos dois modos os demais tokens são os mesmos.
 */
public class Lexer {

    public enum Modo { COMPILADOR, EDITOR }

    private static final int JOINHA = 0x1F44D;           // 👍
    private static final int TOM_PELE_INICIO = 0x1F3FB;  // 👍🏻 ... 👍🏿
    private static final int TOM_PELE_FIM = 0x1F3FF;
    private static final int SELETOR_VARIACAO = 0xFE0F;
    private static final int BOM = 0xFEFF;               // marca de ordem de bytes (editores do Windows)

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
            Map.entry("padoPRINTLN", TokenType.ESCRITA_LINHA),
            Map.entry("pad", TokenType.SE),
            Map.entry("oin", TokenType.SENAO),
            Map.entry("papapadoin", TokenType.REPETICAO),
            Map.entry("padoinquanto", TokenType.ENQUANTO),
            Map.entry("padBREAK", TokenType.INTERROMPE),
            Map.entry("padCONTINUE", TokenType.PROSSEGUE),
            Map.entry("padUNCTION", TokenType.FUNCAO),
            Map.entry("padOUT", TokenType.RETORNO),
            Map.entry("padoTRUE", TokenType.VERDADEIRO),
            Map.entry("padoFALSE", TokenType.FALSO),
            Map.entry("padAND", TokenType.E),
            Map.entry("padOR", TokenType.OU),
            Map.entry("padNOT", TokenType.NAO),
            Map.entry("padoMOD", TokenType.MODULO)
    );

    private final int[] cp;
    private final Modo modo;
    private int pos = 0;
    private int linha = 1;
    private int coluna = 1;
    private int inicio;   // índice em cp do primeiro code point do lexema atual
    private final List<Token> tokens = new ArrayList<>();
    private final List<Diagnostico> erros = new ArrayList<>();

    public Lexer(String fonte) {
        this(fonte, Modo.COMPILADOR);
    }

    public Lexer(String fonte, Modo modo) {
        this.cp = fonte.codePoints().toArray();
        this.modo = modo;
        // Um BOM no início do arquivo não faz parte do programa: é ignorado sem contar coluna
        if (cp.length > 0 && cp[0] == BOM) {
            pos = 1;
        }
    }

    public List<Token> tokenizar() {
        while (!fim()) {
            int c = atual();

            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                avancar();
                continue;
            }

            int l = linha, col = coluna; // posição de início do lexema
            inicio = pos;

            if (c == '#') {
                comentario(l, col);
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
        tokens.add(new Token(TokenType.EOF, "<fim>", linha, coluna, 0));
        return tokens;
    }

    // ---------------------------------------------------------------- padrões

    /** Diagnósticos léxicos encontrados no modo EDITOR (no modo COMPILADOR fica sempre vazia). */
    public List<Diagnostico> erros() {
        return erros;
    }

    /** #[^\n]*  — comentário até o fim da linha: descartado, ou um token COMENTARIO no modo EDITOR. */
    private void comentario(int l, int col) {
        while (!fim() && atual() != '\n') {
            avancar();
        }
        if (modo == Modo.EDITOR) {
            emitir(TokenType.COMENTARIO, trecho(), l, col);
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
                falha("L1", "número real malformado '" + sb + "' (esperado dígito após o ponto)", l, col, pos - inicio);
                return;
            }
            lerDigitos(sb);
            tipo = TokenType.NUM_FLOAT;
        }

        if (!fim() && ehLetra(atual())) {
            // O trecho apontado vai até o fim do "identificador" (ex.: 2x inteiro)
            int fimTrecho = pos;
            while (fimTrecho < cp.length && (ehLetra(cp[fimTrecho]) || ehDigito(cp[fimTrecho]))) {
                fimTrecho++;
            }
            falhaSemToken("L2", "lexema inválido '" + sb + Character.toString(atual())
                    + "...' (identificadores não podem começar com dígito)", l, col, fimTrecho - inicio);
            while (pos < fimTrecho) {
                avancar();
            }
            emitir(TokenType.ERRO, trecho(), l, col);
            return;
        }
        emitir(tipo, sb.toString(), l, col);
    }

    /** "( [^"\\\n] | \\[nt"\\] )*"  — o lexema guarda aspas e escapes, compatíveis com C. */
    private void string(int l, int col) {
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(avancar()); // aspa de abertura
        boolean escapeInvalido = false;   // só chega a true no modo EDITOR

        while (true) {
            if (fim() || atual() == '\n') {
                // No modo EDITOR o trecho até o fim da linha vira ERRO e a análise segue na próxima linha
                falha("L3", "string não fechada", l, col, semCr(pos) - inicio);
                return;
            }
            int c = avancar();
            if (c == '"') {
                sb.append('"');
                break;
            }
            if (c == '\\') {
                if (fim() || "nt\"\\".indexOf(atual()) < 0) {
                    boolean temProximo = !fim() && atual() != '\n';
                    falhaSemToken("L4", "sequência de escape inválida na string", linha, coluna - 1,
                            temProximo ? 2 : 1);
                    // Modo EDITOR: pula o escape e continua; a string inteira vira ERRO no fim
                    escapeInvalido = true;
                    if (temProximo) {
                        avancar();
                    }
                    continue;
                }
                sb.append('\\').appendCodePoint(avancar());
            } else {
                sb.appendCodePoint(c);
            }
        }
        if (escapeInvalido) {
            emitir(TokenType.ERRO, trecho(), l, col);
        } else {
            emitir(TokenType.STRING, sb.toString(), l, col);
        }
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
                if (casar('=')) {
                    emitir(TokenType.DIFERENTE, l, col);
                } else {
                    falha("L5", "'!' isolado não é um operador válido (use !=)", l, col, 1);
                }
            }
            case '(' -> emitir(TokenType.ABRE_PAR, l, col);
            case ')' -> emitir(TokenType.FECHA_PAR, l, col);
            case '{' -> emitir(TokenType.ABRE_CHAVE, l, col);
            case '}' -> emitir(TokenType.FECHA_CHAVE, l, col);
            case ',' -> emitir(TokenType.VIRGULA, l, col);
            default -> falha("L6",
                    "caractere inválido '" + Character.toString(c) + "' (U+" + String.format("%04X", c) + ")", l, col, 1);
        }
    }

    // ---------------------------------------------------------------- erros

    /**
     * Erro léxico. No modo COMPILADOR lança a exceção; no modo EDITOR registra o diagnóstico e emite
     * um token ERRO com o trecho já consumido desde o início do lexema.
     */
    private void falha(String codigo, String mensagem, int l, int col, int tamanho) {
        falhaSemToken(codigo, mensagem, l, col, tamanho);
        emitir(TokenType.ERRO, trecho(), l, col);
    }

    /** Como falha(), mas sem emitir o token (usado quando o lexema ainda não terminou). */
    private void falhaSemToken(String codigo, String mensagem, int l, int col, int tamanho) {
        Diagnostico d = Diagnostico.erro(Fase.LEXICO, codigo, l, col, tamanho, mensagem);
        if (modo == Modo.COMPILADOR) {
            throw new LexicoException(d);
        }
        erros.add(d);
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
        tokens.add(new Token(tipo, lexema, l, col, tamanho(lexema, tipo)));
    }

    /** O tamanho é o do trecho consumido; em COMENTARIO e ERRO o '\r' final (CRLF) fica de fora. */
    private int tamanho(String lexema, TokenType tipo) {
        return (tipo == TokenType.COMENTARIO || tipo == TokenType.ERRO) ? semCr(pos) - inicio : pos - inicio;
    }

    /** O texto do fonte desde o início do lexema atual, sem o '\r' de um fim de linha CRLF. */
    private String trecho() {
        return new String(cp, inicio, semCr(pos) - inicio);
    }

    /** Recua um índice de fim de trecho para antes de um '\r' final. */
    private int semCr(int fimTrecho) {
        return (fimTrecho > inicio && cp[fimTrecho - 1] == '\r') ? fimTrecho - 1 : fimTrecho;
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

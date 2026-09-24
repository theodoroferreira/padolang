package padolang.gerador;

import java.util.List;

/**
 * Funções auxiliares do C gerado (o "runtime" da padolang). Cada uma só é emitida se o
 * programa precisar dela, junto com as que ela usa. A ordem do enum é a ordem de emissão:
 * quem é usado vem antes de quem usa.
 *
 * Todos os nomes começam com "padolang_", que nunca colide com um identificador do usuário
 * (esses sempre ganham o prefixo "pad_").
 */
enum Auxiliar {

    ERRO(List.of(), """
            /* Erro de execução: mesmo formato das outras fases, código de saída 1. */
            static void padolang_erro(int linha, const char *mensagem, const char *entrada) {
                fflush(stdout);
                fprintf(stderr, "padoERRO de execução (linha %d): %s", linha, mensagem);
                if (entrada != NULL) {
                    fprintf(stderr, ": \\"%s\\"", entrada);
                }
                fputc('\\n', stderr);
                exit(1);
            }
            """),

    INTER_TEXTO(List.of(), """
            /* padoINTER -> texto (concatenação). */
            static char *padolang_inter_texto(int x) {
                char *s = malloc(12);
                sprintf(s, "%d", x);
                return s;
            }
            """),

    REAL_TEXTO(List.of(), """
            /*
             * padoFLOAT -> texto, no formato do Double.toString do Java: o menor número de dígitos
             * que representa o valor, sempre com parte decimal, e notação científica quando
             * |x| >= 10^7 ou |x| < 10^-3.  7.0 -> "7.0"   0.0001 -> "1.0E-4"   1e7 -> "1.0E7"
             */
            static char *padolang_real_texto(double x) {
                char *s = malloc(40);
                if (isnan(x)) { strcpy(s, "NaN"); return s; }
                if (isinf(x)) { strcpy(s, x > 0 ? "Infinity" : "-Infinity"); return s; }
                if (x == 0.0) { strcpy(s, signbit(x) ? "-0.0" : "0.0"); return s; }

                /* menor quantidade de dígitos significativos que, lida de volta, dá o mesmo double */
                char e[40];
                for (int p = 1; p <= 17; p++) {
                    snprintf(e, sizeof e, "%.*e", p - 1, x);
                    if (strtod(e, NULL) == x) break;
                }
                /* e = [-]d.ddde[+-]XX  ->  dígitos sem o ponto + expoente */
                int negativo = e[0] == '-';
                char dig[20];
                int n = 0;
                const char *c = e + negativo;
                for (; *c != 'e'; c++) {
                    if (*c != '.') dig[n++] = *c;
                }
                int exp = atoi(c + 1);
                double ax = negativo ? -x : x;

                char *o = s;
                if (negativo) *o++ = '-';
                if (ax >= 1e-3 && ax < 1e7) {
                    if (exp >= 0) {
                        for (int i = 0; i <= exp; i++) *o++ = i < n ? dig[i] : '0';
                        *o++ = '.';
                        if (exp + 1 < n) {
                            for (int i = exp + 1; i < n; i++) *o++ = dig[i];
                        } else {
                            *o++ = '0';
                        }
                    } else {
                        *o++ = '0';
                        *o++ = '.';
                        for (int i = 0; i < -exp - 1; i++) *o++ = '0';
                        for (int i = 0; i < n; i++) *o++ = dig[i];
                    }
                    *o = '\\0';
                } else {
                    *o++ = dig[0];
                    *o++ = '.';
                    if (n > 1) {
                        for (int i = 1; i < n; i++) *o++ = dig[i];
                    } else {
                        *o++ = '0';
                    }
                    sprintf(o, "E%d", exp);
                }
                return s;
            }
            """),

    IMPRIMIR_REAL(List.of(REAL_TEXTO), """
            static void padolang_imprimir_real(double x) {
                char *s = padolang_real_texto(x);
                fputs(s, stdout);
                free(s);
            }
            """),

    CONCATENAR(List.of(), """
            /* texto + texto. A memória não é liberada: os programas padolang são curtos. */
            static char *padolang_concatenar(const char *a, const char *b) {
                size_t na = strlen(a), nb = strlen(b);
                char *s = malloc(na + nb + 1);
                memcpy(s, a, na);
                memcpy(s + na, b, nb + 1);
                return s;
            }
            """),

    DIVIDIR(List.of(ERRO), """
            /* Divisão inteira com o comportamento do Java: erro se o divisor for 0. */
            static int padolang_dividir(int a, int b, int linha) {
                if (b == 0) padolang_erro(linha, "divisão por zero", NULL);
                if (b == -1) return (int) (0u - (unsigned) a);   /* INT_MIN / -1 dá a volta */
                return a / b;
            }
            """),

    RESTO(List.of(ERRO), """
            /* padoMOD com o comportamento do Java: erro se o divisor for 0; sinal do dividendo. */
            static int padolang_resto(int a, int b, int linha) {
                if (b == 0) padolang_erro(linha, "divisão por zero (padoMOD)", NULL);
                if (b == -1) return 0;
                return a % b;
            }
            """),

    PARA_INTER(List.of(ERRO, REAL_TEXTO), """
            /* padoINTER(x): trunca em direção a zero; fora da faixa de int é erro. */
            static int padolang_para_inter(double x, int linha) {
                if (isnan(x) || x >= 2147483648.0 || x <= -2147483649.0) {
                    char msg[80];
                    char *valor = padolang_real_texto(x);
                    snprintf(msg, sizeof msg, "padoINTER(%s): valor fora da faixa de padoINTER", valor);
                    padolang_erro(linha, msg, NULL);
                }
                return (int) x;
            }
            """),

    LER_LINHA(List.of(ERRO), """
            /* Lê uma linha inteira, sem o '\\n' (e sem o '\\r' de arquivos do Windows). */
            static char *padolang_ler_linha(int linha) {
                size_t capacidade = 64, n = 0;
                char *s = malloc(capacidade);
                int c;
                while ((c = getchar()) != EOF && c != '\\n') {
                    if (n + 1 >= capacidade) {
                        capacidade *= 2;
                        s = realloc(s, capacidade);
                    }
                    s[n++] = (char) c;
                }
                if (c == EOF && n == 0) {
                    padolang_erro(linha, "fim da entrada: não há mais nada para o padoIN ler", NULL);
                }
                if (n > 0 && s[n - 1] == '\\r') n--;
                s[n] = '\\0';
                return s;
            }
            """),

    LER_INTER(List.of(LER_LINHA), """
            /* padoIN de padoINTER: a linha precisa ter só um inteiro (espaços em volta são ignorados). */
            static int padolang_ler_inter(int linha) {
                char *s = padolang_ler_linha(linha);
                char *fim;
                errno = 0;
                long v = strtol(s, &fim, 10);
                while (*fim == ' ' || *fim == '\\t') fim++;
                if (fim == s || *fim != '\\0' || errno == ERANGE || v < INT_MIN || v > INT_MAX) {
                    padolang_erro(linha, "entrada inválida para padoINTER", s);
                }
                free(s);
                return (int) v;
            }
            """),

    LER_REAL(List.of(LER_LINHA), """
            /* padoIN de padoFLOAT: a linha precisa ter só um número, com ponto decimal. */
            static double padolang_ler_real(int linha) {
                char *s = padolang_ler_linha(linha);
                char *fim;
                double v = strtod(s, &fim);
                while (*fim == ' ' || *fim == '\\t') fim++;
                /* strtod também aceita "inf", "nan" e hexadecimal, que a padolang não aceita */
                if (fim == s || *fim != '\\0' || strspn(s, "0123456789+-.eE \\t") != strlen(s)) {
                    padolang_erro(linha, "entrada inválida para padoFLOAT", s);
                }
                free(s);
                return v;
            }
            """),

    LER_OLEAN(List.of(LER_LINHA), """
            /* padoIN de padoOLEAN: a linha precisa ser padoTRUE ou padoFALSE. */
            static bool padolang_ler_olean(int linha) {
                char *s = padolang_ler_linha(linha);
                char *ini = s + strspn(s, " \\t");
                size_t n = strlen(ini);
                while (n > 0 && (ini[n - 1] == ' ' || ini[n - 1] == '\\t')) ini[--n] = '\\0';
                bool v = strcmp(ini, "padoTRUE") == 0;
                if (!v && strcmp(ini, "padoFALSE") != 0) {
                    padolang_erro(linha, "entrada inválida para padoOLEAN (use padoTRUE ou padoFALSE)", s);
                }
                free(s);
                return v;
            }
            """);

    final List<Auxiliar> dependencias;
    final String codigo;

    Auxiliar(List<Auxiliar> dependencias, String codigo) {
        this.dependencias = dependencias;
        this.codigo = codigo;
    }
}

package padolang.lsp.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON mínimo, escrito à mão (o projeto não tem dependências), para o servidor LSP.
 *
 * Representação: objeto = Map<String,Object> (LinkedHashMap, mantém a ordem das chaves), array =
 * List<Object>, string = String, número = Long (inteiro) ou Double, true/false = Boolean, null = null.
 *
 * Strings aceitam todos os escapes, inclusive \\uXXXX. Um caractere fora do plano básico, como o 👍,
 * pode vir como par surrogate (\\ud83d\\udc4d): cada \\u vira um char Java, e os dois juntos formam o
 * mesmo par que o Java usa internamente, então o texto sai certo sem tratamento especial.
 * Na escrita, só aspas, barra invertida e caracteres de controle são escapados; o resto sai como está
 * (em UTF-8, na camada JsonRpc).
 */
public final class Json {

    private final String texto;
    private int pos = 0;

    private Json(String texto) {
        this.texto = texto;
    }

    // ------------------------------------------------------------------ leitura

    /** Lê um valor JSON; lança JsonException se o texto não for JSON válido. */
    public static Object ler(String texto) {
        Json p = new Json(texto);
        p.espacos();
        Object valor = p.valor();
        p.espacos();
        if (p.pos < texto.length()) {
            throw p.erro("texto sobrando depois do valor");
        }
        return valor;
    }

    private Object valor() {
        if (pos >= texto.length()) throw erro("fim inesperado");
        char c = texto.charAt(pos);
        return switch (c) {
            case '{' -> objeto();
            case '[' -> array();
            case '"' -> string();
            case 't' -> palavra("true", Boolean.TRUE);
            case 'f' -> palavra("false", Boolean.FALSE);
            case 'n' -> palavra("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) yield numero();
                throw erro("caractere inesperado '" + c + "'");
            }
        };
    }

    private Map<String, Object> objeto() {
        Map<String, Object> obj = new LinkedHashMap<>();
        pos++; // {
        espacos();
        if (aceitar('}')) return obj;
        do {
            espacos();
            if (pos >= texto.length() || texto.charAt(pos) != '"') throw erro("esperado nome entre aspas");
            String chave = string();
            espacos();
            exigir(':');
            espacos();
            obj.put(chave, valor());
            espacos();
        } while (aceitar(','));
        exigir('}');
        return obj;
    }

    private List<Object> array() {
        List<Object> lista = new ArrayList<>();
        pos++; // [
        espacos();
        if (aceitar(']')) return lista;
        do {
            espacos();
            lista.add(valor());
            espacos();
        } while (aceitar(','));
        exigir(']');
        return lista;
    }

    private String string() {
        pos++; // aspa de abertura
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= texto.length()) throw erro("string sem aspa de fechamento");
            char c = texto.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c < 0x20) throw erro("caractere de controle dentro de string");
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= texto.length()) throw erro("escape incompleto");
            char e = texto.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > texto.length()) throw erro("escape \\u incompleto");
                    try {
                        sb.append((char) Integer.parseInt(texto.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw erro("escape \\u inválido");
                    }
                    pos += 4;
                }
                default -> throw erro("escape inválido '\\" + e + "'");
            }
        }
    }

    private Object numero() {
        int inicio = pos;
        aceitar('-');
        digitos();
        boolean inteiro = true;
        if (aceitar('.')) {
            inteiro = false;
            digitos();
        }
        if (pos < texto.length() && (texto.charAt(pos) == 'e' || texto.charAt(pos) == 'E')) {
            inteiro = false;
            pos++;
            if (!aceitar('+')) aceitar('-');
            digitos();
        }
        String s = texto.substring(inicio, pos);
        try {
            return inteiro ? (Object) Long.parseLong(s) : (Object) Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return Double.parseDouble(s);   // inteiro grande demais para long
        }
    }

    private void digitos() {
        int inicio = pos;
        while (pos < texto.length() && Character.isDigit(texto.charAt(pos)) && texto.charAt(pos) < 128) pos++;
        if (pos == inicio) throw erro("esperado dígito");
    }

    private Object palavra(String palavra, Object valor) {
        if (!texto.startsWith(palavra, pos)) throw erro("esperado " + palavra);
        pos += palavra.length();
        return valor;
    }

    private void espacos() {
        while (pos < texto.length() && " \t\r\n".indexOf(texto.charAt(pos)) >= 0) pos++;
    }

    private boolean aceitar(char c) {
        if (pos < texto.length() && texto.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void exigir(char c) {
        if (!aceitar(c)) throw erro("esperado '" + c + "'");
    }

    private JsonException erro(String mensagem) {
        return new JsonException(mensagem + " (posição " + pos + ")");
    }

    // ------------------------------------------------------------------ escrita

    /** Escreve um valor (Map, List, String, Number, Boolean ou null) como JSON, numa linha só. */
    public static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(valor, sb);
        return sb.toString();
    }

    private static void escrever(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            string(s, sb);
        } else if (valor instanceof Boolean b) {
            sb.append(b);
        } else if (valor instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) throw new JsonException("número não representável em JSON: " + d);
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append(d.longValue());
            else sb.append(d);
        } else if (valor instanceof Number n) {
            sb.append(n.longValue());
        } else if (valor instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                string(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                escrever(l.get(i), sb);
            }
            sb.append(']');
        } else if (valor instanceof int[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(a[i]);
            }
            sb.append(']');
        } else {
            throw new JsonException("tipo sem representação JSON: " + valor.getClass().getName());
        }
    }

    private static void string(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ montagem e acesso

    /** Monta um objeto a partir de pares chave, valor: objeto("line", 0, "character", 4). */
    public static Map<String, Object> objeto(Object... chavesEValores) {
        Map<String, Object> obj = new LinkedHashMap<>();
        for (int i = 0; i + 1 < chavesEValores.length; i += 2) {
            obj.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return obj;
    }

    /** Segue um caminho de chaves dentro de objetos: campo(msg, "params", "textDocument", "uri"). */
    public static Object campo(Object valor, String... caminho) {
        for (String chave : caminho) {
            if (!(valor instanceof Map<?, ?> m)) return null;
            valor = m.get(chave);
        }
        return valor;
    }

    /** Como campo(), mas exige uma String. */
    public static String texto(Object valor, String... caminho) {
        Object v = campo(valor, caminho);
        if (v instanceof String s) return s;
        throw new JsonException("campo " + String.join(".", caminho) + " ausente ou não é texto");
    }
}

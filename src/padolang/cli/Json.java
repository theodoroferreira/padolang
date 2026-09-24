package padolang.cli;

import padolang.diagnostico.Diagnostico;

import java.util.List;
import java.util.stream.Collectors;

/** Saída JSON do "check --json": uma lista de diagnósticos, numa linha só. */
final class Json {

    private Json() {
    }

    /** Ex.: [{"fase":"SEMANTICO","codigo":"R14","linha":3,"coluna":17,"tamanho":1,"mensagem":"..."}] */
    static String diagnosticos(List<Diagnostico> diagnosticos) {
        return diagnosticos.stream().map(Json::diagnostico).collect(Collectors.joining(",", "[", "]"));
    }

    private static String diagnostico(Diagnostico d) {
        return "{\"fase\":" + texto(d.fase().name())
                + ",\"severidade\":" + texto(d.severidade().name())
                + ",\"codigo\":" + texto(d.codigo())
                + ",\"linha\":" + d.linha()
                + ",\"coluna\":" + d.coluna()
                + ",\"tamanho\":" + d.tamanho()
                + ",\"mensagem\":" + texto(d.mensagem()) + "}";
    }

    /** String JSON: aspas, barra invertida e caracteres de controle escapados; o resto sai em UTF-8. */
    static String texto(String s) {
        StringBuilder sb = new StringBuilder("\"");
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
        return sb.append('"').toString();
    }
}

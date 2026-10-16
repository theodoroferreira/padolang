package padolang.lsp;

import padolang.lsp.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Transporte JSON-RPC do LSP sobre stdin/stdout.
 *
 * Cada mensagem é um cabeçalho e um corpo:
 *
 *   Content-Length: 52\r\n
 *   \r\n
 *   {"jsonrpc":"2.0","id":1,"method":"initialize",...}
 *
 * O Content-Length conta BYTES do corpo em UTF-8, não caracteres: um 👍 no texto do documento são
 * 4 bytes. Por isso os dois lados trabalham com os fluxos crus, em bytes. Nada além do protocolo pode
 * ir para o stdout; o log vai para o stderr.
 */
public final class JsonRpc {

    private final InputStream entrada;
    private final OutputStream saida;

    public JsonRpc(InputStream entrada, OutputStream saida) {
        this.entrada = entrada;
        this.saida = saida;
    }

    /**
     * Lê o corpo da próxima mensagem, como texto (o JSON ainda não interpretado).
     * Devolve null no fim da entrada. Lança IOException se o cabeçalho estiver malformado.
     */
    public String ler() throws IOException {
        int tamanho = -1;
        while (true) {
            String linha = linhaDoCabecalho();
            if (linha == null) return null;             // fim da entrada
            if (linha.isEmpty()) {
                if (tamanho >= 0) break;                // fim do cabeçalho
                continue;                               // linhas vazias soltas entre mensagens
            }
            int doisPontos = linha.indexOf(':');
            if (doisPontos < 0) throw new IOException("cabeçalho malformado: " + linha);
            String nome = linha.substring(0, doisPontos).trim().toLowerCase(Locale.ROOT);
            if (nome.equals("content-length")) {
                try {
                    tamanho = Integer.parseInt(linha.substring(doisPontos + 1).trim());
                } catch (NumberFormatException e) {
                    throw new IOException("Content-Length inválido: " + linha);
                }
            }
            // Content-Type e outros cabeçalhos são ignorados (o corpo é sempre UTF-8)
        }
        byte[] corpo = entrada.readNBytes(tamanho);
        if (corpo.length < tamanho) return null;         // a entrada acabou no meio da mensagem
        return new String(corpo, StandardCharsets.UTF_8);
    }

    /** Escreve uma mensagem (um objeto JSON) com o cabeçalho Content-Length em bytes. */
    public synchronized void escrever(Object mensagem) throws IOException {
        byte[] corpo = Json.escrever(mensagem).getBytes(StandardCharsets.UTF_8);
        saida.write(("Content-Length: " + corpo.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        saida.write(corpo);
        saida.flush();
    }

    /** Uma linha do cabeçalho, sem o \r\n; null se a entrada acabou antes de qualquer byte. */
    private String linhaDoCabecalho() throws IOException {
        ByteArrayOutputStream linha = new ByteArrayOutputStream();
        while (true) {
            int b = entrada.read();
            if (b < 0) {
                return linha.size() == 0 ? null : linha.toString(StandardCharsets.US_ASCII);
            }
            if (b == '\n') break;
            linha.write(b);
        }
        String s = linha.toString(StandardCharsets.US_ASCII);
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }
}

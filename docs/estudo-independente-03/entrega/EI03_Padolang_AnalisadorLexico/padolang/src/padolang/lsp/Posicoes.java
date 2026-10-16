package padolang.lsp;

import java.util.Map;
import padolang.lsp.json.Json;

/**
 * Conversão entre as posições da padolang e as do LSP.
 *
 *   - padolang: linha e coluna começam em 1 e contam CODE POINTS (é assim que o léxico anda);
 *   - LSP:      linha e caractere começam em 0 e contam UNIDADES UTF-16 (o padrão do protocolo).
 *
 * As duas contagens só diferem fora do plano básico do Unicode: o 👍 (U+1F44D) é 1 code point e
 * 2 unidades UTF-16; o 👍🏽 são 2 code points e 4 unidades. Numa linha com um 👍 antes, a coluna 20 da
 * padolang é o caractere 20 do LSP (19 + 1 unidade a mais pelo 👍).
 *
 * Só o '\n' quebra linha, como no léxico; um '\r' antes dele fica fora do fim da linha. O BOM no
 * início do arquivo não conta coluna no léxico, mas o editor o vê como um caractere da linha 0.
 */
public final class Posicoes {

    private static final int BOM = 0xFEFF;

    private final String[] linhas;
    private final int deslocamentoBom;   // 1 se o arquivo começa com BOM (só afeta a linha 0)

    public Posicoes(String fonte) {
        this.linhas = fonte.split("\n", -1);
        this.deslocamentoBom = (!fonte.isEmpty() && fonte.codePointAt(0) == BOM) ? 1 : 0;
    }

    /** Número de linhas do LSP (um arquivo terminado em '\n' tem uma última linha vazia). */
    public int quantasLinhas() {
        return linhas.length;
    }

    /** Caractere LSP (UTF-16, a partir de 0) da coluna da padolang (code points, a partir de 1). */
    public int utf16(int linha, int coluna) {
        return utf16DoDeslocamento(linha, coluna - 1);
    }

    /** Tamanho em unidades UTF-16 de um trecho de 'tamanho' code points que começa na coluna dada. */
    public int tamanhoUtf16(int linha, int coluna, int tamanho) {
        return utf16DoDeslocamento(linha, coluna - 1 + tamanho) - utf16(linha, coluna);
    }

    /** Posição LSP ({line, character}) da linha e coluna da padolang. */
    public Map<String, Object> posicao(int linha, int coluna) {
        return Json.objeto("line", Math.max(linha - 1, 0), "character", utf16(linha, coluna));
    }

    /** Intervalo LSP ({start, end}) de um trecho da padolang; nunca passa do fim da linha. */
    public Map<String, Object> intervalo(int linha, int coluna, int tamanho) {
        return Json.objeto(
                "start", posicao(linha, coluna),
                "end", Json.objeto("line", Math.max(linha - 1, 0),
                        "character", utf16DoDeslocamento(linha, coluna - 1 + tamanho)));
    }

    /** A posição logo depois do último caractere do documento (fim do edit de formatação). */
    public Map<String, Object> fimDoDocumento() {
        int ultima = linhas.length - 1;
        return Json.objeto("line", ultima, "character", linhas[ultima].length());
    }

    /**
     * Soma as unidades UTF-16 dos primeiros 'codePoints' code points da linha (a partir de 1),
     * parando no fim da linha (sem o '\r'). Linha inexistente vira a posição 0.
     */
    private int utf16DoDeslocamento(int linha, int codePoints) {
        if (linha < 1 || linha > linhas.length) return 0;
        String texto = linhas[linha - 1];
        int fim = texto.endsWith("\r") ? texto.length() - 1 : texto.length();
        int i = 0;
        if (linha == 1 && deslocamentoBom == 1) {
            i = 1;                              // o BOM: 1 unidade UTF-16 que o léxico não conta
        }
        for (int n = 0; n < codePoints && i < fim; n++) {
            i += Character.charCount(texto.codePointAt(i));
        }
        return Math.min(i, fim);
    }
}

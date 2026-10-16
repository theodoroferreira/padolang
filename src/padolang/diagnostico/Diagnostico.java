package padolang.diagnostico;

/**
 * Um problema encontrado no fonte, em forma estruturada: é o que a CLI (--json) e o editor (LSP)
 * consomem. O texto "padoERRO ..." mostrado no terminal é só uma forma de exibi-lo (formatar()).
 *
 * Códigos: L1..L7 (léxico), S1 (sintático) e R1..R21 (semântico, como em docs/semantica.md).
 * Linha e coluna começam em 1 e contam code points; tamanho é o número de code points do trecho
 * apontado (0 quando não há trecho, como no fim do arquivo).
 */
public record Diagnostico(Fase fase, Severidade severidade, String codigo,
                          int linha, int coluna, int tamanho, String mensagem) {

    public static Diagnostico erro(Fase fase, String codigo, int linha, int coluna, int tamanho, String mensagem) {
        return new Diagnostico(fase, Severidade.ERRO, codigo, linha, coluna, tamanho, mensagem);
    }

    /** Ex.: "padoERRO semântico (linha 3, coluna 17): divisão por zero" */
    public String formatar() {
        return String.format("%s %s (linha %d, coluna %d): %s",
                severidade.prefixo, fase.nome, linha, coluna, mensagem);
    }
}

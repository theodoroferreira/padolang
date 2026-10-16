package padolang.ast;

import java.util.List;

/** padUNCTION <Tipo> id ( <Params> ) { <Bloco> }   — a posição é a do nome da função. */
public record Funcao(Tipo tipoRetorno, String nome, List<Parametro> parametros,
                     List<Comando> corpo, int linha, int coluna) {

    public record Parametro(Tipo tipo, String nome, int linha, int coluna) {
    }
}

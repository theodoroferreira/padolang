package padolang.ast;

import java.util.List;

/** padUNCTION <Tipo> id ( <Params> ) { <Bloco> } */
public record Funcao(Tipo tipoRetorno, String nome, List<Parametro> parametros,
                     List<Comando> corpo, int linha) {

    public record Parametro(Tipo tipo, String nome, int linha) {
    }
}

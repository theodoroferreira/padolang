package padolang.ast;

import java.util.List;

/**
 * Nós de comando (tudo que pode aparecer dentro de um <Bloco>).
 * A interface é selada: o compilador Java garante que todo switch sobre
 * Comando trate todos os casos — útil na semântica e no gerador.
 */
public sealed interface Comando {

    int linha();

    /** padoin <Tipo> id [ = <Expressao> ] 👍   (inicial pode ser null) */
    record Declaracao(Tipo tipo, String nome, Expressao inicial, int linha) implements Comando {
    }

    /** id = <Expressao> */
    record Atribuicao(String nome, Expressao valor, int linha) implements Comando {
    }

    /** id++  ou  id-- */
    record Incremento(String nome, boolean decremento, int linha) implements Comando {
    }

    /** padoIN ( id ) 👍 */
    record Leitura(String nome, int linha) implements Comando {
    }

    /** padoPRINT ( <Expressao> ) 👍 */
    record Escrita(Expressao valor, int linha) implements Comando {
    }

    /**
     * pad ( cond ) { entao } [ oin { senao } ]
     * senao é null quando não há 'oin'. Um "oin pad ..." (senão-se) vira
     * uma lista senao contendo um único Se.
     */
    record Se(Comparacao condicao, List<Comando> entao, List<Comando> senao, int linha) implements Comando {
    }

    /** papapadoin ( [inicio] , cond , [passo] ) { corpo }   (inicio e passo podem ser null) */
    record Repeticao(Comando inicio, Comparacao condicao, Comando passo,
                     List<Comando> corpo, int linha) implements Comando {
    }

    /** padOUT <Expressao> 👍 */
    record Retorno(Expressao valor, int linha) implements Comando {
    }
}

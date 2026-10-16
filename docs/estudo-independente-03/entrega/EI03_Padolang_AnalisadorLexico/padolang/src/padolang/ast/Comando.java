package padolang.ast;

import java.util.List;

/**
 * Nós de comando (tudo que pode aparecer dentro de um <Bloco>).
 * A interface é selada: o compilador Java garante que todo switch sobre
 * Comando trate todos os casos, o que ajuda na semântica e no gerador.
 *
 * A posição (linha, coluna) é a do nome da variável em Declaracao, Atribuicao, Incremento e
 * Leitura, e a da palavra reservada que abre o comando nos demais.
 */
public sealed interface Comando {

    int linha();

    int coluna();

    /** padoin <Tipo> id [ = <Expressao> ] 👍   (inicial pode ser null) */
    record Declaracao(Tipo tipo, String nome, Expressao inicial, int linha, int coluna) implements Comando {
    }

    /** id = <Expressao> */
    record Atribuicao(String nome, Expressao valor, int linha, int coluna) implements Comando {
    }

    /** id++  ou  id-- */
    record Incremento(String nome, boolean decremento, int linha, int coluna) implements Comando {
    }

    /** id ( [args] ) 👍 — chamada de função como comando; o valor devolvido é descartado. */
    record ChamadaComando(Expressao.Chamada chamada, int linha, int coluna) implements Comando {
    }

    /** padoIN ( id ) 👍 */
    record Leitura(String nome, int linha, int coluna) implements Comando {
    }

    /**
     * padoPRINT ( <Expressao> ) 👍   ou   padoPRINTLN ( [ <Expressao> ] ) 👍
     * valor só é null em padoPRINTLN(), que apenas pula a linha.
     */
    record Escrita(Expressao valor, boolean pulaLinha, int linha, int coluna) implements Comando {
    }

    /**
     * pad ( cond ) { entao } [ oin { senao } ]
     * senao é null quando não há 'oin'. Um "oin pad ..." (senão-se) vira
     * uma lista senao contendo um único Se.
     */
    record Se(Expressao condicao, List<Comando> entao, List<Comando> senao,
              int linha, int coluna) implements Comando {
    }

    /** papapadoin ( [inicio] , cond , [passo] ) { corpo }   (inicio e passo podem ser null) */
    record Repeticao(Comando inicio, Expressao condicao, Comando passo, List<Comando> corpo,
                     int linha, int coluna) implements Comando {
    }

    /** padoinquanto ( cond ) { corpo } */
    record Enquanto(Expressao condicao, List<Comando> corpo, int linha, int coluna) implements Comando {
    }

    /** padOUT <Expressao> 👍 */
    record Retorno(Expressao valor, int linha, int coluna) implements Comando {
    }

    /** padBREAK 👍 — sai do laço mais interno. */
    record Interrompe(int linha, int coluna) implements Comando {
    }

    /** padCONTINUE 👍 — vai para a próxima volta do laço mais interno. */
    record Prossegue(int linha, int coluna) implements Comando {
    }
}

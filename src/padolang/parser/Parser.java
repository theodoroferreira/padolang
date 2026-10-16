package padolang.parser;

import padolang.ast.Comando;
import padolang.ast.Expressao;
import padolang.ast.Funcao;
import padolang.ast.Operador;
import padolang.ast.OperadorUnario;
import padolang.ast.Programa;
import padolang.ast.Tipo;
import padolang.lexer.Token;
import padolang.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static padolang.lexer.TokenType.*;

/**
 * Analisador sintático preditivo por descida recursiva.
 *
 * Cada não terminal da EBNF corresponde a um método com o mesmo nome, e o
 * comentário acima de cada método traz a produção que ele reconhece.
 * A gramática não tem recursão à esquerda e as repetições { ... } viram
 * laços while. Basta olhar o token atual para decidir qual produção aplicar (LL(1)).
 *
 * O parser só valida a FORMA do programa. Perguntas como "a variável existe?"
 * ou "os tipos batem?" ficam para a análise semântica.
 */
public class Parser {

    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // <Programa> ::= { <Funcao> } "PADOIN" "{" <Bloco> "}"
    public Programa programa() {
        List<Funcao> funcoes = new ArrayList<>();
        while (verifica(FUNCAO)) {
            funcoes.add(funcao());
        }

        consumir(PROGRAMA, "'PADOIN' (início do programa) ou 'padUNCTION'");
        consumir(ABRE_CHAVE);
        List<Comando> principal = bloco();
        consumir(FECHA_CHAVE);
        consumir(EOF, "fim do arquivo após o '}' que fecha o PADOIN");

        return new Programa(funcoes, principal);
    }

    // <Funcao> ::= "padUNCTION" <Tipo> id "(" [ <Params> ] ")" "{" <Bloco> "}"
    private Funcao funcao() {
        consumir(FUNCAO);
        Tipo tipoRetorno = tipo();
        Token nome = consumir(ID, "nome da função");

        consumir(ABRE_PAR);
        List<Funcao.Parametro> parametros = new ArrayList<>();
        if (!verifica(FECHA_PAR)) {
            parametros = params();
        }
        consumir(FECHA_PAR);

        consumir(ABRE_CHAVE);
        List<Comando> corpo = bloco();
        consumir(FECHA_CHAVE);

        return new Funcao(tipoRetorno, nome.lexema(), parametros, corpo, nome.linha(), nome.coluna());
    }

    // <Params> ::= <Tipo> id { "," <Tipo> id }
    private List<Funcao.Parametro> params() {
        List<Funcao.Parametro> lista = new ArrayList<>();
        do {
            Tipo t = tipo();
            Token nome = consumir(ID, "nome do parâmetro");
            lista.add(new Funcao.Parametro(t, nome.lexema(), nome.linha(), nome.coluna()));
        } while (aceitar(VIRGULA));
        return lista;
    }

    // <Bloco> ::= { <Declaracao> | <Comando> }
    // O bloco termina quando aparece '}'; quem consome a chave é quem chamou.
    private List<Comando> bloco() {
        List<Comando> comandos = new ArrayList<>();
        while (!verifica(FECHA_CHAVE) && !verifica(EOF)) {
            if (verifica(DECLARA)) {
                comandos.add(declaracao());
            } else {
                comandos.add(comando());
            }
        }
        return comandos;
    }

    // <Tipo> ::= "padoINTER" | "padoFLOAT" | "padoOLEAN" | "padoSTRING"
    private Tipo tipo() {
        Token t = atual();
        Tipo tipo = switch (t.tipo()) {
            case TIPO_INTER -> Tipo.INTER;
            case TIPO_FLOAT -> Tipo.FLOAT;
            case TIPO_OLEAN -> Tipo.OLEAN;
            case TIPO_STRING -> Tipo.STRING;
            default -> throw new SintaticoException(
                    "um tipo (padoINTER, padoFLOAT, padoOLEAN ou padoSTRING)", t);
        };
        avancar();
        return tipo;
    }

    // <Declaracao> ::= "padoin" <Tipo> id [ "=" <Expressao> ] "👍"
    private Comando declaracao() {
        consumir(DECLARA);
        Tipo t = tipo();
        Token nome = consumir(ID, "nome da variável");

        Expressao inicial = null;
        if (aceitar(ATRIB)) {
            inicial = expressao();
        }
        consumir(JOINHA, "'👍' ao final da declaração");

        return new Comando.Declaracao(t, nome.lexema(), inicial, nome.linha(), nome.coluna());
    }

    // <Comando> ::= <ComandoId> "👍" | <Leitura> | <Escrita> | <Condicional> | <Repeticao>
    //             | <Enquanto> | <Retorno> | "padBREAK" "👍" | "padCONTINUE" "👍"
    private Comando comando() {
        Token t = atual();
        return switch (t.tipo()) {
            case ID -> {
                Comando c = comandoId();
                consumir(JOINHA, c instanceof Comando.ChamadaComando
                        ? "'👍' ao final da chamada" : "'👍' ao final da atribuição");
                yield c;
            }
            case LEITURA -> leitura();
            case ESCRITA, ESCRITA_LINHA -> escrita();
            case SE -> condicional();
            case REPETICAO -> repeticao();
            case ENQUANTO -> enquanto();
            case RETORNO -> retorno();
            case INTERROMPE -> {
                avancar();
                consumir(JOINHA, "'👍' após 'padBREAK'");
                yield new Comando.Interrompe(t.linha(), t.coluna());
            }
            case PROSSEGUE -> {
                avancar();
                consumir(JOINHA, "'👍' após 'padCONTINUE'");
                yield new Comando.Prossegue(t.linha(), t.coluna());
            }
            default -> throw new SintaticoException("uma declaração ou um comando", t);
        };
    }

    // <ComandoId> ::= id ( <RestoAtribuicao> | "(" [ <Args> ] ")" )
    // Atribuição e chamada começam com id; com a fatoração, o parser consome o id e decide
    // pelo token que vem depois dele, sem olhar mais de um token à frente (LL(1)).
    private Comando comandoId() {
        Token id = consumir(ID);
        if (verifica(ABRE_PAR)) {
            Expressao.Chamada ch = chamada(id);
            return new Comando.ChamadaComando(ch, id.linha(), id.coluna());
        }
        return restoAtribuicao(id);
    }

    // <Atribuicao> ::= id <RestoAtribuicao>
    // Usada no início e no passo do papapadoin.
    private Comando atribuicao() {
        return restoAtribuicao(consumir(ID, "nome da variável"));
    }

    // <RestoAtribuicao> ::= "=" <Expressao> | "++" | "--"
    // Recebe o id já consumido por quem chamou.
    private Comando restoAtribuicao(Token id) {
        if (aceitar(ATRIB)) {
            return new Comando.Atribuicao(id.lexema(), expressao(), id.linha(), id.coluna());
        }
        if (aceitar(INCREMENTO)) {
            return new Comando.Incremento(id.lexema(), false, id.linha(), id.coluna());
        }
        if (aceitar(DECREMENTO)) {
            return new Comando.Incremento(id.lexema(), true, id.linha(), id.coluna());
        }
        throw new SintaticoException("'=', '++' ou '--' após '" + id.lexema() + "'", atual());
    }

    // <Leitura> ::= "padoIN" "(" id ")" "👍"
    private Comando leitura() {
        consumir(LEITURA);
        consumir(ABRE_PAR);
        Token nome = consumir(ID, "a variável que vai receber a leitura");
        consumir(FECHA_PAR);
        consumir(JOINHA, "'👍' ao final do padoIN");
        return new Comando.Leitura(nome.lexema(), nome.linha(), nome.coluna());
    }

    // <Escrita> ::= "padoPRINT" "(" <Expressao> ")" "👍"
    //             | "padoPRINTLN" "(" [ <Expressao> ] ")" "👍"
    private Comando escrita() {
        Token inicio = avancar();
        boolean pulaLinha = inicio.tipo() == ESCRITA_LINHA;
        consumir(ABRE_PAR);
        Expressao valor = null;
        if (!pulaLinha || !verifica(FECHA_PAR)) {
            valor = expressao();
        }
        consumir(FECHA_PAR);
        consumir(JOINHA, "'👍' ao final do " + inicio.lexema());
        return new Comando.Escrita(valor, pulaLinha, inicio.linha(), inicio.coluna());
    }

    // <Condicional> ::= "pad" "(" <Expressao> ")" "{" <Bloco> "}"
    //                   [ "oin" ( "{" <Bloco> "}" | <Condicional> ) ]
    private Comando condicional() {
        Token inicio = consumir(SE);
        consumir(ABRE_PAR);
        Expressao cond = expressao();
        consumir(FECHA_PAR);

        consumir(ABRE_CHAVE);
        List<Comando> entao = bloco();
        consumir(FECHA_CHAVE);

        List<Comando> senao = null;
        if (aceitar(SENAO)) {
            if (verifica(SE)) {
                senao = List.of(condicional());           // oin pad (...) { }: senão-se
            } else {
                consumir(ABRE_CHAVE, "'{' ou 'pad' após 'oin'");
                senao = bloco();
                consumir(FECHA_CHAVE);
            }
        }
        return new Comando.Se(cond, entao, senao, inicio.linha(), inicio.coluna());
    }

    // <Repeticao> ::= "papapadoin" "(" [ <Atribuicao> ] "," <Expressao> ","
    //                 [ <Atribuicao> ] ")" "{" <Bloco> "}"
    private Comando repeticao() {
        Token inicio = consumir(REPETICAO);
        consumir(ABRE_PAR);

        Comando ini = verifica(ID) ? atribuicao() : null;
        consumir(VIRGULA, "',' após a inicialização do papapadoin");
        Expressao cond = expressao();
        consumir(VIRGULA, "',' após a condição do papapadoin");
        Comando passo = verifica(ID) ? atribuicao() : null;

        consumir(FECHA_PAR);
        consumir(ABRE_CHAVE);
        List<Comando> corpo = bloco();
        consumir(FECHA_CHAVE);

        return new Comando.Repeticao(ini, cond, passo, corpo, inicio.linha(), inicio.coluna());
    }

    // <Enquanto> ::= "padoinquanto" "(" <Expressao> ")" "{" <Bloco> "}"
    private Comando enquanto() {
        Token inicio = consumir(ENQUANTO);
        consumir(ABRE_PAR);
        Expressao cond = expressao();
        consumir(FECHA_PAR);
        consumir(ABRE_CHAVE);
        List<Comando> corpo = bloco();
        consumir(FECHA_CHAVE);
        return new Comando.Enquanto(cond, corpo, inicio.linha(), inicio.coluna());
    }

    // <Retorno> ::= "padOUT" <Expressao> "👍"
    private Comando retorno() {
        Token inicio = consumir(RETORNO);
        Expressao valor = expressao();
        consumir(JOINHA, "'👍' ao final do padOUT");
        return new Comando.Retorno(valor, inicio.linha(), inicio.coluna());
    }

    // ------------------------------------------------------------ expressões
    //
    // Um nível da gramática por nível de precedência, do mais fraco para o mais forte.
    // Cada nível chama o seguinte, então quem está mais embaixo agrupa primeiro:
    //   padOR < padAND < == != < relacionais < + - < * / padoMOD < unários < fator
    // Os laços { } constroem a árvore inclinada à esquerda: a - b - c  =>  (a - b) - c

    private static final Map<TokenType, Operador> OPERADORES = Map.ofEntries(
            Map.entry(OU, Operador.OU), Map.entry(E, Operador.E),
            Map.entry(IGUAL, Operador.IGUAL), Map.entry(DIFERENTE, Operador.DIFERENTE),
            Map.entry(MENOR, Operador.MENOR), Map.entry(MENOR_IGUAL, Operador.MENOR_IGUAL),
            Map.entry(MAIOR, Operador.MAIOR), Map.entry(MAIOR_IGUAL, Operador.MAIOR_IGUAL),
            Map.entry(MAIS, Operador.SOMA), Map.entry(MENOS, Operador.SUBTRACAO),
            Map.entry(VEZES, Operador.MULTIPLICACAO), Map.entry(DIVIDIDO, Operador.DIVISAO),
            Map.entry(MODULO, Operador.MODULO));

    // <Expressao> ::= <Ou>
    private Expressao expressao() {
        return ou();
    }

    // <Ou> ::= <E> { "padOR" <E> }
    private Expressao ou() {
        Expressao esq = e();
        while (verifica(OU)) {
            esq = binaria(esq, this::e);
        }
        return esq;
    }

    // <E> ::= <Igualdade> { "padAND" <Igualdade> }
    private Expressao e() {
        Expressao esq = igualdade();
        while (verifica(E)) {
            esq = binaria(esq, this::igualdade);
        }
        return esq;
    }

    // <Igualdade> ::= <Relacional> { ( "==" | "!=" ) <Relacional> }
    private Expressao igualdade() {
        Expressao esq = relacional();
        while (verifica(IGUAL) || verifica(DIFERENTE)) {
            esq = binaria(esq, this::relacional);
        }
        return esq;
    }

    // <Relacional> ::= <Aditiva> { ( "<" | "<=" | ">" | ">=" ) <Aditiva> }
    private Expressao relacional() {
        Expressao esq = aditiva();
        while (verifica(MENOR) || verifica(MENOR_IGUAL) || verifica(MAIOR) || verifica(MAIOR_IGUAL)) {
            esq = binaria(esq, this::aditiva);
        }
        return esq;
    }

    // <Aditiva> ::= <Multiplicativa> { ( "+" | "-" ) <Multiplicativa> }
    private Expressao aditiva() {
        Expressao esq = multiplicativa();
        while (verifica(MAIS) || verifica(MENOS)) {
            esq = binaria(esq, this::multiplicativa);
        }
        return esq;
    }

    // <Multiplicativa> ::= <Unaria> { ( "*" | "/" | "padoMOD" ) <Unaria> }
    private Expressao multiplicativa() {
        Expressao esq = unaria();
        while (verifica(VEZES) || verifica(DIVIDIDO) || verifica(MODULO)) {
            esq = binaria(esq, this::unaria);
        }
        return esq;
    }

    /** Consome o operador atual e monta o nó com o operando da direita lido pelo nível seguinte. */
    private Expressao binaria(Expressao esq, java.util.function.Supplier<Expressao> proximoNivel) {
        Token op = avancar();
        Expressao dir = proximoNivel.get();
        return new Expressao.Binaria(esq, OPERADORES.get(op.tipo()), dir, op.linha(), op.coluna());
    }

    // <Unaria> ::= ( "-" | "padNOT" ) <Unaria> | <Fator>
    private Expressao unaria() {
        Token t = atual();
        if (aceitar(MENOS)) {
            return new Expressao.Unaria(OperadorUnario.NEGATIVO, unaria(), t.linha(), t.coluna());
        }
        if (aceitar(NAO)) {
            return new Expressao.Unaria(OperadorUnario.NAO, unaria(), t.linha(), t.coluna());
        }
        return fator();
    }

    // <Fator> ::= id [ "(" [ <Args> ] ")" ] | "padoINTER" "(" <Expressao> ")"
    //           | num_int | num_float | string | "padoTRUE" | "padoFALSE" | "(" <Expressao> ")"
    private Expressao fator() {
        Token t = atual();
        switch (t.tipo()) {
            case ID -> {
                avancar();
                if (verifica(ABRE_PAR)) {                 // id seguido de '(' => chamada
                    return chamada(t);
                }
                return new Expressao.Variavel(t.lexema(), t.linha(), t.coluna());
            }
            case TIPO_INTER -> {                          // padoINTER(x): conversão explícita
                avancar();
                consumir(ABRE_PAR, "'(' após 'padoINTER' (conversão: padoINTER(valor))");
                Expressao valor = expressao();
                consumir(FECHA_PAR);
                return new Expressao.ConversaoInter(valor, t.linha(), t.coluna());
            }
            case NUM_INT -> {
                avancar();
                return new Expressao.LiteralInteiro(t.lexema(), t.linha(), t.coluna());
            }
            case NUM_FLOAT -> {
                avancar();
                return new Expressao.LiteralReal(t.lexema(), t.linha(), t.coluna());
            }
            case STRING -> {
                avancar();
                return new Expressao.LiteralTexto(t.lexema(), t.linha(), t.coluna());
            }
            case VERDADEIRO, FALSO -> {
                avancar();
                return new Expressao.LiteralLogico(t.tipo() == VERDADEIRO, t.linha(), t.coluna());
            }
            case ABRE_PAR -> {
                avancar();
                Expressao interna = expressao();
                consumir(FECHA_PAR);
                return interna;                           // parênteses não geram nó
            }
            default -> throw new SintaticoException("uma expressão", t);
        }
    }

    // "(" [ <Args> ] ")" depois do id da função, em expressão ou como comando.
    // Recebe o id já consumido por quem chamou.
    private Expressao.Chamada chamada(Token nome) {
        consumir(ABRE_PAR);
        List<Expressao> args = new ArrayList<>();
        if (!verifica(FECHA_PAR)) {
            args = args();
        }
        consumir(FECHA_PAR);
        return new Expressao.Chamada(nome.lexema(), args, nome.linha(), nome.coluna());
    }

    // <Args> ::= <Expressao> { "," <Expressao> }
    private List<Expressao> args() {
        List<Expressao> lista = new ArrayList<>();
        do {
            lista.add(expressao());
        } while (aceitar(VIRGULA));
        return lista;
    }

    // ------------------------------------------------------------ auxiliares

    private Token atual() {
        return tokens.get(pos);
    }

    private Token avancar() {
        Token t = tokens.get(pos);
        if (t.tipo() != EOF) {
            pos++;
        }
        return t;
    }

    private boolean verifica(TokenType tipo) {
        return atual().tipo() == tipo;
    }

    /** Consome o token se ele for do tipo pedido; senão não faz nada. */
    private boolean aceitar(TokenType tipo) {
        if (verifica(tipo)) {
            avancar();
            return true;
        }
        return false;
    }

    /** Exige o token; se não vier, interrompe com erro sintático. */
    private Token consumir(TokenType tipo) {
        return consumir(tipo, tipo.descricao());
    }

    private Token consumir(TokenType tipo, String esperado) {
        if (!verifica(tipo)) {
            throw new SintaticoException(esperado, atual());
        }
        return avancar();
    }
}

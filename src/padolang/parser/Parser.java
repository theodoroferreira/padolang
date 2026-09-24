package padolang.parser;

import padolang.ast.Comando;
import padolang.ast.Comparacao;
import padolang.ast.Expressao;
import padolang.ast.Funcao;
import padolang.ast.OpAritmetico;
import padolang.ast.OpRelacional;
import padolang.ast.Programa;
import padolang.ast.Tipo;
import padolang.lexer.Token;
import padolang.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

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
        Token inicio = consumir(FUNCAO);
        Tipo tipoRetorno = tipo();
        String nome = consumir(ID, "nome da função").lexema();

        consumir(ABRE_PAR);
        List<Funcao.Parametro> parametros = new ArrayList<>();
        if (!verifica(FECHA_PAR)) {
            parametros = params();
        }
        consumir(FECHA_PAR);

        consumir(ABRE_CHAVE);
        List<Comando> corpo = bloco();
        consumir(FECHA_CHAVE);

        return new Funcao(tipoRetorno, nome, parametros, corpo, inicio.linha());
    }

    // <Params> ::= <Tipo> id { "," <Tipo> id }
    private List<Funcao.Parametro> params() {
        List<Funcao.Parametro> lista = new ArrayList<>();
        do {
            int linha = atual().linha();
            Tipo t = tipo();
            String nome = consumir(ID, "nome do parâmetro").lexema();
            lista.add(new Funcao.Parametro(t, nome, linha));
        } while (aceitar(VIRGULA));
        return lista;
    }

    // <Bloco> ::= { <Declaracao> | <Comando> }
    // O bloco termina quando aparece '}' — quem consome a chave é quem chamou.
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
        Token inicio = consumir(DECLARA);
        Tipo t = tipo();
        String nome = consumir(ID, "nome da variável").lexema();

        Expressao inicial = null;
        if (aceitar(ATRIB)) {
            inicial = expressao();
        }
        consumir(JOINHA, "'👍' ao final da declaração");

        return new Comando.Declaracao(t, nome, inicial, inicio.linha());
    }

    // <Comando> ::= <Atribuicao> "👍" | <Leitura> | <Escrita>
    //             | <Condicional> | <Repeticao> | <Retorno>
    private Comando comando() {
        return switch (atual().tipo()) {
            case ID -> {
                Comando c = atribuicao();
                consumir(JOINHA, "'👍' ao final da atribuição");
                yield c;
            }
            case LEITURA -> leitura();
            case ESCRITA -> escrita();
            case SE -> condicional();
            case REPETICAO -> repeticao();
            case RETORNO -> retorno();
            default -> throw new SintaticoException("uma declaração ou um comando", atual());
        };
    }

    // <Atribuicao> ::= id "=" <Expressao> | id "++" | id "--"
    private Comando atribuicao() {
        Token id = consumir(ID, "nome da variável");

        if (aceitar(ATRIB)) {
            return new Comando.Atribuicao(id.lexema(), expressao(), id.linha());
        }
        if (aceitar(INCREMENTO)) {
            return new Comando.Incremento(id.lexema(), false, id.linha());
        }
        if (aceitar(DECREMENTO)) {
            return new Comando.Incremento(id.lexema(), true, id.linha());
        }
        throw new SintaticoException("'=', '++' ou '--' após '" + id.lexema() + "'", atual());
    }

    // <Leitura> ::= "padoIN" "(" id ")" "👍"
    private Comando leitura() {
        Token inicio = consumir(LEITURA);
        consumir(ABRE_PAR);
        String nome = consumir(ID, "a variável que vai receber a leitura").lexema();
        consumir(FECHA_PAR);
        consumir(JOINHA, "'👍' ao final do padoIN");
        return new Comando.Leitura(nome, inicio.linha());
    }

    // <Escrita> ::= "padoPRINT" "(" <Expressao> ")" "👍"
    private Comando escrita() {
        Token inicio = consumir(ESCRITA);
        consumir(ABRE_PAR);
        Expressao valor = expressao();
        consumir(FECHA_PAR);
        consumir(JOINHA, "'👍' ao final do padoPRINT");
        return new Comando.Escrita(valor, inicio.linha());
    }

    // <Condicional> ::= "pad" "(" <ExprLogica> ")" "{" <Bloco> "}"
    //                   [ "oin" ( "{" <Bloco> "}" | <Condicional> ) ]
    private Comando condicional() {
        Token inicio = consumir(SE);
        consumir(ABRE_PAR);
        Comparacao cond = exprLogica();
        consumir(FECHA_PAR);

        consumir(ABRE_CHAVE);
        List<Comando> entao = bloco();
        consumir(FECHA_CHAVE);

        List<Comando> senao = null;
        if (aceitar(SENAO)) {
            if (verifica(SE)) {
                senao = List.of(condicional());           // oin pad (...) { } — senão-se
            } else {
                consumir(ABRE_CHAVE, "'{' ou 'pad' após 'oin'");
                senao = bloco();
                consumir(FECHA_CHAVE);
            }
        }
        return new Comando.Se(cond, entao, senao, inicio.linha());
    }

    // <Repeticao> ::= "papapadoin" "(" [ <Atribuicao> ] "," <ExprLogica> ","
    //                 [ <Atribuicao> ] ")" "{" <Bloco> "}"
    private Comando repeticao() {
        Token inicio = consumir(REPETICAO);
        consumir(ABRE_PAR);

        Comando ini = verifica(ID) ? atribuicao() : null;
        consumir(VIRGULA, "',' após a inicialização do papapadoin");
        Comparacao cond = exprLogica();
        consumir(VIRGULA, "',' após a condição do papapadoin");
        Comando passo = verifica(ID) ? atribuicao() : null;

        consumir(FECHA_PAR);
        consumir(ABRE_CHAVE);
        List<Comando> corpo = bloco();
        consumir(FECHA_CHAVE);

        return new Comando.Repeticao(ini, cond, passo, corpo, inicio.linha());
    }

    // <Retorno> ::= "padOUT" <Expressao> "👍"
    private Comando retorno() {
        Token inicio = consumir(RETORNO);
        Expressao valor = expressao();
        consumir(JOINHA, "'👍' ao final do padOUT");
        return new Comando.Retorno(valor, inicio.linha());
    }

    // <ExprLogica> ::= <Expressao> <OpRel> <Expressao>
    private Comparacao exprLogica() {
        int linha = atual().linha();
        Expressao esq = expressao();
        OpRelacional op = opRel();
        Expressao dir = expressao();
        return new Comparacao(esq, op, dir, linha);
    }

    // <OpRel> ::= "==" | "!=" | ">" | ">=" | "<" | "<="
    private OpRelacional opRel() {
        Token t = atual();
        OpRelacional op = switch (t.tipo()) {
            case IGUAL -> OpRelacional.IGUAL;
            case DIFERENTE -> OpRelacional.DIFERENTE;
            case MENOR -> OpRelacional.MENOR;
            case MENOR_IGUAL -> OpRelacional.MENOR_IGUAL;
            case MAIOR -> OpRelacional.MAIOR;
            case MAIOR_IGUAL -> OpRelacional.MAIOR_IGUAL;
            default -> throw new SintaticoException(
                    "um operador relacional (==, !=, <, <=, >, >=)", t);
        };
        avancar();
        return op;
    }

    // <Expressao> ::= <Termo> { ( "+" | "-" ) <Termo> }
    // O laço constrói a árvore inclinada à esquerda: a - b - c  =>  (a - b) - c
    private Expressao expressao() {
        Expressao esq = termo();
        while (verifica(MAIS) || verifica(MENOS)) {
            Token op = avancar();
            OpAritmetico o = op.tipo() == MAIS ? OpAritmetico.SOMA : OpAritmetico.SUBTRACAO;
            esq = new Expressao.Binaria(esq, o, termo(), op.linha());
        }
        return esq;
    }

    // <Termo> ::= <Fator> { ( "*" | "/" ) <Fator> }
    // Como <Termo> fica abaixo de <Expressao>, * e / são agrupados antes de + e -.
    private Expressao termo() {
        Expressao esq = fator();
        while (verifica(VEZES) || verifica(DIVIDIDO)) {
            Token op = avancar();
            OpAritmetico o = op.tipo() == VEZES ? OpAritmetico.MULTIPLICACAO : OpAritmetico.DIVISAO;
            esq = new Expressao.Binaria(esq, o, fator(), op.linha());
        }
        return esq;
    }

    // <Fator> ::= id [ "(" [ <Args> ] ")" ] | num_int | num_float | string | bool
    //           | "(" <Expressao> ")" | "-" <Fator>
    private Expressao fator() {
        Token t = atual();
        switch (t.tipo()) {
            case ID -> {
                avancar();
                if (aceitar(ABRE_PAR)) {                  // id seguido de '(' => chamada
                    List<Expressao> args = new ArrayList<>();
                    if (!verifica(FECHA_PAR)) {
                        args = args();
                    }
                    consumir(FECHA_PAR);
                    return new Expressao.Chamada(t.lexema(), args, t.linha());
                }
                return new Expressao.Variavel(t.lexema(), t.linha());
            }
            case NUM_INT -> {
                avancar();
                return new Expressao.LiteralInteiro(t.lexema(), t.linha());
            }
            case NUM_FLOAT -> {
                avancar();
                return new Expressao.LiteralReal(t.lexema(), t.linha());
            }
            case STRING -> {
                avancar();
                return new Expressao.LiteralTexto(t.lexema(), t.linha());
            }
            case VERDADEIRO, FALSO -> {
                avancar();
                return new Expressao.LiteralLogico(t.tipo() == VERDADEIRO, t.linha());
            }
            case ABRE_PAR -> {
                avancar();
                Expressao interna = expressao();
                consumir(FECHA_PAR);
                return interna;                           // parênteses não geram nó
            }
            case MENOS -> {
                avancar();
                return new Expressao.Negacao(fator(), t.linha());
            }
            default -> throw new SintaticoException("uma expressão", t);
        }
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

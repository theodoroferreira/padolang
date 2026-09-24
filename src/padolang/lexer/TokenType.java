package padolang.lexer;

/**
 * Categorias de token da Padolang (uma por linha da tabela de tokens do relatório).
 * A descrição é usada nas mensagens de erro ("esperado '👍'", "esperado identificador").
 */
public enum TokenType {
    // Palavras reservadas
    PROGRAMA("'PADOIN'"),
    DECLARA("'padoin'"),
    TIPO_INTER("'padoINTER'"),
    TIPO_FLOAT("'padoFLOAT'"),
    TIPO_OLEAN("'padoOLEAN'"),
    TIPO_STRING("'padoSTRING'"),
    LEITURA("'padoIN'"),
    ESCRITA("'padoPRINT'"),
    SE("'pad'"),
    SENAO("'oin'"),
    REPETICAO("'papapadoin'"),
    FUNCAO("'padUNCTION'"),
    RETORNO("'padOUT'"),
    VERDADEIRO("'padoTRUE'"),
    FALSO("'padoFALSE'"),

    // Identificadores e literais
    ID("identificador"),
    NUM_INT("número inteiro"),
    NUM_FLOAT("número real"),
    STRING("texto entre aspas"),

    // Operadores aritméticos
    MAIS("'+'"), MENOS("'-'"), VEZES("'*'"), DIVIDIDO("'/'"),
    INCREMENTO("'++'"), DECREMENTO("'--'"),

    // Atribuição e operadores relacionais
    ATRIB("'='"), IGUAL("'=='"), DIFERENTE("'!='"),
    MENOR("'<'"), MENOR_IGUAL("'<='"), MAIOR("'>'"), MAIOR_IGUAL("'>='"),

    // Delimitadores
    ABRE_PAR("'('"), FECHA_PAR("')'"), ABRE_CHAVE("'{'"), FECHA_CHAVE("'}'"),
    VIRGULA("','"), JOINHA("'👍'"),

    EOF("fim do arquivo");

    private final String descricao;

    TokenType(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}

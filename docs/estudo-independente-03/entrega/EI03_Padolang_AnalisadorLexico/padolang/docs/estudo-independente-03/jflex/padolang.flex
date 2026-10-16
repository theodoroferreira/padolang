/*
 * Especificação léxica da padolang na notação do JFlex.
 *
 * O analisador léxico do compilador é o src/padolang/lexer/Lexer.java, escrito à mão. Este arquivo
 * é a especificação formal dos mesmos tokens: as expressões regulares do relatório, numa forma que
 * o JFlex aceita e executa. Serve para conferir a especificação, e não faz parte do compilador:
 * o scanner gerado a partir dele precisa listar exatamente os mesmos tokens que o Lexer.java
 * (ver o LEIAME, "Conferência com o JFlex").
 *
 * As duas regras de desempate do JFlex são as do relatório (Problema 2):
 *   1. maior casamento: entre os padrões que casam, vale o que consome o lexema mais longo;
 *   2. prioridade: no empate de tamanho, vale a regra escrita primeiro. Por isso as palavras
 *      reservadas vêm antes do identificador, e os padrões de erro vêm depois dos válidos.
 */

import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

%%

%public
%class EspecificacaoLexica
%unicode
%line
%column
%type String

%{
    private boolean fim = false;

    /* O yycolumn do JFlex conta unidades UTF-16, e a padolang conta code points: um 👍 (U+1F44D)
       ocupa uma coluna, e não duas. Estes campos guardam quantos pares surrogate já passaram na
       linha atual, para descontar da coluna. */
    private int linhaDosPares = 0;
    private int pares = 0;

    /** A coluna do lexema atual (a partir de 1), em code points. */
    private int coluna() {
        if (yyline != linhaDosPares) {
            linhaDosPares = yyline;
            pares = 0;
        }
        int coluna = yycolumn + 1 - pares;

        String lexema = yytext();
        int ultimaQuebra = lexema.lastIndexOf('\n');
        if (ultimaQuebra >= 0) {            // comentário de bloco: a contagem recomeça na última linha dele
            linhaDosPares = yyline + (int) lexema.chars().filter(c -> c == '\n').count();
            pares = 0;
        }
        String resto = lexema.substring(ultimaQuebra + 1);
        pares += resto.length() - resto.codePointCount(0, resto.length());
        return coluna;
    }

    /** Uma linha da listagem: linha, coluna, classe e lexema. */
    private String token(String classe, String lexema) {
        return String.format("%-5d %-5d %-13s %s", yyline + 1, coluna(), classe,
                lexema.replace("\r", "").replace("\n", "\\n"));
    }

    private String token(String classe) {
        return token(classe, yytext());
    }

    /** Trecho inválido: o '\r' de um fim de linha CRLF não faz parte dele. */
    private String erro() {
        String lexema = yytext();
        return token("ERRO", lexema.endsWith("\r") ? lexema.substring(0, lexema.length() - 1) : lexema);
    }

    /** java EspecificacaoLexica arquivo.padoin: lista os tokens, um por linha. */
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        try (Reader fonte = new InputStreamReader(Files.newInputStream(Path.of(args[0])), StandardCharsets.UTF_8)) {
            EspecificacaoLexica scanner = new EspecificacaoLexica(fonte);
            out.printf("%-5s %-5s %-13s %s%n", "LIN", "COL", "TIPO", "LEXEMA");
            for (String linha = scanner.yylex(); linha != null; linha = scanner.yylex()) {
                out.println(linha);
            }
        }
    }
%}

/* ---------------------------------------------------------------- definições regulares */

LETRA       = [a-zA-Z_]
DIGITO      = [0-9]

ID          = {LETRA} ({LETRA} | {DIGITO})*
NUM_INT     = {DIGITO}+
NUM_FLOAT   = {DIGITO}+ "." {DIGITO}+

ESCAPE      = \\ [nt\"\\]
STRING      = \" ( [^\"\\\n] | {ESCAPE} )* \"

/* 👍, com modificador de tom de pele (U+1F3FB a U+1F3FF) e seletor de variação (U+FE0F) opcionais */
JOINHA      = \u{1F44D} [\u{1F3FB}-\u{1F3FF}]? \u{FE0F}?

BRANCO      = [ \t\r\n]+
COM_LINHA   = "#" ( [^*\n] [^\n]* )?
COM_BLOCO   = "#*" ( [^*] | \*+ [^*#] )* \*+ "#"

/* Padrões de erro: lexemas que parecem um token, mas estão malformados */
NUM_SEM_FRACAO   = {DIGITO}+ "."
NUM_DOIS_PONTOS  = {NUM_FLOAT} "." ( {DIGITO} | "." )*
NUM_COM_LETRA    = ( {NUM_INT} | {NUM_FLOAT} ) {LETRA} ( {LETRA} | {DIGITO} )*
CORPO_STRING     = ( [^\"\\\n] | \\ [^\n] )*
STRING_ESCAPE    = \" {CORPO_STRING} \"
STRING_ABERTA    = \" {CORPO_STRING} \\?
BLOCO_ABERTO     = "#*" ( [^*] | \*+ [^*#] )* \**

%%

/* ---------------------------------------------------------------- regras */

/* a) Palavras reservadas: antes do identificador (regra de prioridade) */
"PADOIN"        { return token("PROGRAMA"); }
"padoin"        { return token("DECLARA"); }
"padoINTER"     { return token("TIPO_INTER"); }
"padoFLOAT"     { return token("TIPO_FLOAT"); }
"padoOLEAN"     { return token("TIPO_OLEAN"); }
"padoSTRING"    { return token("TIPO_STRING"); }
"padoIN"        { return token("LEITURA"); }
"padoPRINT"     { return token("ESCRITA"); }
"padoPRINTLN"   { return token("ESCRITA_LINHA"); }
"pad"           { return token("SE"); }
"oin"           { return token("SENAO"); }
"papapadoin"    { return token("REPETICAO"); }
"padoinquanto"  { return token("ENQUANTO"); }
"padBREAK"      { return token("INTERROMPE"); }
"padCONTINUE"   { return token("PROSSEGUE"); }
"padUNCTION"    { return token("FUNCAO"); }
"padOUT"        { return token("RETORNO"); }
"padoTRUE"      { return token("VERDADEIRO"); }
"padoFALSE"     { return token("FALSO"); }

/* d) Operadores lógicos e o resto da divisão, escritos como palavras reservadas */
"padAND"        { return token("E"); }
"padOR"         { return token("OU"); }
"padNOT"        { return token("NAO"); }
"padoMOD"       { return token("MODULO"); }

/* b) Identificadores e c) números */
{ID}            { return token("ID"); }
{NUM_INT}       { return token("NUM_INT"); }
{NUM_FLOAT}     { return token("NUM_FLOAT"); }
{STRING}        { return token("STRING"); }

/* d) Operadores aritméticos */
"++"            { return token("INCREMENTO"); }
"--"            { return token("DECREMENTO"); }
"+"             { return token("MAIS"); }
"-"             { return token("MENOS"); }
"*"             { return token("VEZES"); }
"/"             { return token("DIVIDIDO"); }

/* d) Operadores relacionais e e) atribuição */
"=="            { return token("IGUAL"); }
"!="            { return token("DIFERENTE"); }
"<="            { return token("MENOR_IGUAL"); }
">="            { return token("MAIOR_IGUAL"); }
"<"             { return token("MENOR"); }
">"             { return token("MAIOR"); }
"="             { return token("ATRIB"); }

/* e) Delimitadores e pontuação */
"("             { return token("ABRE_PAR"); }
")"             { return token("FECHA_PAR"); }
"{"             { return token("ABRE_CHAVE"); }
"}"             { return token("FECHA_CHAVE"); }
","             { return token("VIRGULA"); }
{JOINHA}        { return token("JOINHA", "👍"); }

/* f) Espaços em branco (descartados) e comentários */
{BRANCO}        { /* ignorado */ }
{COM_LINHA}     { return token("COMENTARIO"); }
{COM_BLOCO}     { return token("COMENTARIO"); }

/* Erros léxicos: sempre depois dos padrões válidos, que ganham no empate de tamanho */
{NUM_SEM_FRACAO}    { return erro(); }    /* L1: 3.        */
{NUM_DOIS_PONTOS}   { return erro(); }    /* L1: 1.2.3     */
{NUM_COM_LETRA}     { return erro(); }    /* L2: 2x        */
{STRING_ESCAPE}     { return erro(); }    /* L4: "a\qb"    */
{STRING_ABERTA}     { return erro(); }    /* L3: "aberto   */
{BLOCO_ABERTO}      { return erro(); }    /* L7: #* aberto */
[^]                 { return erro(); }    /* L5 e L6: "!" sozinho e qualquer símbolo fora do alfabeto */

<<EOF>>             { if (fim) return null; fim = true; return token("EOF", "<fim>"); }

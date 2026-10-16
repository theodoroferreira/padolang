package padolang.semantica;

import padolang.ast.Comando;
import padolang.ast.Expressao;
import padolang.ast.Funcao;
import padolang.ast.Operador;
import padolang.ast.OperadorUnario;
import padolang.ast.Programa;
import padolang.ast.Tipo;
import padolang.diagnostico.Diagnostico;
import padolang.diagnostico.Fase;
import padolang.semantica.TabelaDeSimbolos.Simbolo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Análise semântica: percorre a AST e verifica o que a gramática não consegue expressar.
 * Fica entre o parser e o gerador; o gerador só roda se nenhum erro for encontrado.
 *
 * Estruturas:
 *   - tabela de FUNÇÕES (nome -> Funcao), montada antes de tudo. Por isso uma função pode
 *     chamar outra declarada depois dela, ou a si mesma (recursão);
 *   - tabela de símbolos das VARIÁVEIS, com pilha de escopos (ver TabelaDeSimbolos).
 *
 * O resultado são as Anotacoes: o tipo de cada expressão, que o gerador consulta.
 *
 * As regras estão numeradas (R1..R21) como em docs/semantica.md. Em resumo:
 *   - atribuir, inicializar, passar argumento e retornar seguem a mesma regra de compatibilidade:
 *     mesmo tipo, ou padoINTER -> padoFLOAT. padoFLOAT -> padoINTER só com padoINTER(x);
 *   - aritmética só com números; + com um texto de um dos lados é concatenação;
 *   - comparações e lógicos produzem padoOLEAN; condições precisam ser padoOLEAN.
 *
 * Quando uma expressão já tem erro, seu tipo vira null ("desconhecido") e as verificações que
 * dependem dele são puladas. Assim um único erro não gera uma cascata de mensagens.
 */
public final class AnalisadorSemantico {

    private static final BigInteger MAIOR_INTER = BigInteger.valueOf(Integer.MAX_VALUE);

    private final Map<String, Funcao> funcoes = new LinkedHashMap<>();
    private final TabelaDeSimbolos simbolos = new TabelaDeSimbolos();
    private final Anotacoes anotacoes = new Anotacoes();
    private final List<Diagnostico> erros = new ArrayList<>();

    /** Função sendo analisada; null enquanto analisamos o PADOIN. */
    private Funcao funcaoAtual;

    /** Quantos laços envolvem o comando atual (padBREAK e padCONTINUE só valem se for > 0). */
    private int lacosAbertos = 0;

    /** Nomes declarados no nível mais externo do PADOIN, só para melhorar uma mensagem de erro. */
    private final Set<String> variaveisDoPrincipal = new HashSet<>();

    public Anotacoes analisar(Programa programa) {
        // 1. Tabela de funções (R9: nomes de função não se repetem)
        for (Funcao f : programa.funcoes()) {
            Funcao anterior = funcoes.putIfAbsent(f.nome(), f);
            if (anterior != null) {
                erro("R9", f, "a função '%s' já foi declarada na linha %d",
                        f.nome(), anterior.linha());
            }
        }
        for (Comando c : programa.principal()) {
            if (c instanceof Comando.Declaracao d) {
                variaveisDoPrincipal.add(d.nome());
            }
        }

        // 2. Corpo de cada função, cada uma com a sua própria pilha de escopos
        for (Funcao f : programa.funcoes()) {
            funcao(f);
        }

        // 3. Bloco principal
        funcaoAtual = null;
        simbolos.abrirEscopo();
        comandos(programa.principal());
        simbolos.fecharEscopo();

        if (!erros.isEmpty()) {
            throw new SemanticoException(erros);
        }
        return anotacoes;
    }

    // ------------------------------------------------------------ funções

    private void funcao(Funcao f) {
        funcaoAtual = f;
        lacosAbertos = 0;
        // Parâmetros e corpo da função ficam no MESMO escopo (como em C e Java):
        // declarar no corpo uma variável com o nome de um parâmetro é redeclaração.
        simbolos.abrirEscopo();
        for (Funcao.Parametro p : f.parametros()) {
            declarar(p.nome(), p.tipo(), p.linha(), p.coluna(), true);
        }
        comandos(f.corpo());
        simbolos.fecharEscopo();

        // R12: toda função devolve um valor, então todo caminho precisa terminar em padOUT
        if (!sempreRetorna(f.corpo())) {
            erro("R12", f, "a função '%s' pode terminar sem padOUT: todo caminho precisa "
                    + "devolver um %s", f.nome(), f.tipoRetorno().palavra());
        }
        funcaoAtual = null;
    }

    /**
     * Um bloco sempre retorna se algum comando dele sempre retorna:
     *   - padOUT sempre retorna;
     *   - pad/oin sempre retorna se tiver 'oin' e os DOIS ramos sempre retornarem;
     *   - laços nunca contam, porque o corpo pode não executar nenhuma vez.
     */
    private static boolean sempreRetorna(List<Comando> bloco) {
        for (Comando c : bloco) {
            if (c instanceof Comando.Retorno) {
                return true;
            }
            if (c instanceof Comando.Se se && se.senao() != null
                    && sempreRetorna(se.entao()) && sempreRetorna(se.senao())) {
                return true;
            }
        }
        return false;
    }

    /**
     * R21: descreve por que a execução nunca passa deste comando para o seguinte, ou devolve null
     * se ela pode passar. padOUT, padBREAK e padCONTINUE sempre desviam; um pad/oin desvia se
     * os dois ramos desviarem. Laços não contam (a condição pode ser falsa logo de início).
     */
    private static String desvia(Comando c) {
        return switch (c) {
            case Comando.Retorno r -> "padOUT";
            case Comando.Interrompe i -> "padBREAK";
            case Comando.Prossegue p -> "padCONTINUE";
            case Comando.Se se when se.senao() != null
                    && desvia(se.entao()) != null && desvia(se.senao()) != null ->
                    "um pad/oin em que todos os caminhos saem do bloco";
            default -> null;
        };
    }

    private static String desvia(List<Comando> bloco) {
        for (Comando c : bloco) {
            String motivo = desvia(c);
            if (motivo != null) {
                return motivo;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ comandos

    /** Cada { } abre um escopo novo; o que for declarado dentro some ao fechar. */
    private void bloco(List<Comando> lista) {
        simbolos.abrirEscopo();
        comandos(lista);
        simbolos.fecharEscopo();
    }

    private void comandos(List<Comando> lista) {
        String desviou = null;
        boolean avisado = false;
        for (Comando c : lista) {
            if (desviou != null && !avisado) {
                erro("R21", c, "código inalcançável: este comando vem depois de %s", desviou);
                avisado = true;   // um erro por bloco basta
            }
            comando(c);
            if (desviou == null) {
                desviou = desvia(c);
            }
        }
    }

    private void comando(Comando c) {
        switch (c) {
            case Comando.Declaracao d -> {
                // O valor inicial é analisado ANTES de declarar: "padoin padoINTER x = x + 1👍" é erro
                if (d.inicial() != null) {
                    Tipo t = tipoDe(d.inicial());
                    compatibilizar("R4", d.tipo(), t, d.inicial(), "a variável '" + d.nome() + "'");
                }
                declarar(d.nome(), d.tipo(), d.linha(), d.coluna(), false);
            }
            case Comando.Atribuicao a -> {
                Simbolo s = variavel(a.nome(), a.linha(), a.coluna());
                Tipo t = tipoDe(a.valor());
                if (s != null) {
                    compatibilizar("R4", s.tipo(), t, a.valor(), "a variável '" + a.nome() + "'");
                }
            }
            case Comando.Incremento inc -> {
                Simbolo s = variavel(inc.nome(), inc.linha(), inc.coluna());
                if (s != null && !s.tipo().numerico()) {           // R13
                    erro("R13", inc, "'%s' só se aplica a padoINTER ou padoFLOAT, mas '%s' é %s",
                            inc.decremento() ? "--" : "++", inc.nome(), s.tipo().palavra());
                }
            }
            case Comando.ChamadaComando ch -> tipoDe(ch.chamada());
            case Comando.Leitura l -> {
                Simbolo s = variavel(l.nome(), l.linha(), l.coluna());
                if (s != null) {
                    anotacoes.registrar(l, s.tipo());
                }
            }
            case Comando.Escrita e -> {
                if (e.valor() != null) {
                    tipoDe(e.valor());
                }
            }
            case Comando.Retorno r -> {
                Tipo t = tipoDe(r.valor());
                if (funcaoAtual == null) {                         // R11
                    erro("R11", r, "padOUT fora de função: o bloco PADOIN não devolve valor");
                } else {
                    compatibilizar("R11", funcaoAtual.tipoRetorno(), t, r.valor(),
                            "o retorno da função '" + funcaoAtual.nome() + "'");
                }
            }
            case Comando.Interrompe i -> dentroDeLaco(i, "padBREAK");
            case Comando.Prossegue p -> dentroDeLaco(p, "padCONTINUE");
            case Comando.Se se -> {
                condicao(se.condicao(), "pad");
                bloco(se.entao());
                if (se.senao() != null) {
                    bloco(se.senao());
                }
            }
            case Comando.Repeticao rep -> {
                if (rep.inicio() != null) {
                    comando(rep.inicio());
                }
                condicao(rep.condicao(), "papapadoin");
                if (rep.passo() != null) {
                    comando(rep.passo());
                }
                laco(rep.corpo());
            }
            case Comando.Enquanto enq -> {
                condicao(enq.condicao(), "padoinquanto");
                laco(enq.corpo());
            }
        }
    }

    private void laco(List<Comando> corpo) {
        lacosAbertos++;
        bloco(corpo);
        lacosAbertos--;
    }

    /** R20: padBREAK e padCONTINUE só fazem sentido dentro de um laço. */
    private void dentroDeLaco(Comando c, String palavra) {
        if (lacosAbertos == 0) {
            erro("R20", c, "%s fora de laço: só pode aparecer dentro de papapadoin ou padoinquanto",
                    palavra);
        }
    }

    /** R16: a condição de pad, papapadoin e padoinquanto precisa ser padoOLEAN (como no Java). */
    private void condicao(Expressao e, String comando) {
        Tipo t = tipoDe(e);
        if (t != null && t != Tipo.OLEAN) {
            erro("R16", e, "a condição do %s precisa ser padoOLEAN, mas é %s", comando, t.palavra());
        }
    }

    // ------------------------------------------------------------ declarações e nomes

    private void declarar(String nome, Tipo tipo, int linha, int coluna, boolean parametro) {
        String oQue = parametro ? "parâmetro" : "variável";

        // R3: uma variável não pode ter o nome de uma função
        if (funcoes.containsKey(nome)) {
            erro("R3", linha, coluna, tamanho(nome), "'%s' já é o nome de uma função e não pode ser usado como %s",
                    nome, oQue);
        }

        // R2: não se declara de novo um nome que ainda está visível (mesmo escopo ou escopo externo)
        Simbolo existente = simbolos.buscar(nome);
        if (existente != null) {
            erro("R2", linha, coluna, tamanho(nome), "'%s' já foi declarado como %s na linha %d", nome,
                    existente.parametro() ? "parâmetro" : "variável", existente.linha());
            return;   // mantém a primeira declaração
        }
        simbolos.declarar(new Simbolo(nome, tipo, linha, parametro));
    }

    /**
     * R1: todo nome usado como variável precisa estar declarado e visível.
     * R15: se o nome for de uma função, a mensagem avisa que faltou chamá-la.
     */
    private Simbolo variavel(String nome, int linha, int coluna) {
        Simbolo s = simbolos.buscar(nome);
        if (s != null) {
            return s;
        }
        if (funcoes.containsKey(nome)) {
            erro("R15", linha, coluna, tamanho(nome), "'%s' é uma função, não uma variável (para chamá-la use %s(...))", nome, nome);
        } else if (funcaoAtual != null && variaveisDoPrincipal.contains(nome)) {
            erro("R1", linha, coluna, tamanho(nome), "variável '%s' não declarada na função '%s' (funções não enxergam as variáveis "
                    + "do PADOIN; passe o valor como parâmetro)", nome, funcaoAtual.nome());
        } else {
            erro("R1", linha, coluna, tamanho(nome), "variável '%s' não declarada", nome);
        }
        return null;
    }

    // ------------------------------------------------------------ tipos

    /**
     * R4/R5: compatibilidade de atribuição, usada em declaração, atribuição, argumento e padOUT.
     * Aceita o mesmo tipo ou padoINTER -> padoFLOAT. O erro aponta para o valor. O código é R5 para
     * padoFLOAT -> padoINTER; nos demais casos é o da regra de quem chamou (R4, R8 ou R11).
     */
    private void compatibilizar(String codigo, Tipo destino, Tipo origem, Expressao valor, String oQue) {
        if (origem == null || destino == origem) {
            return;
        }
        if (destino == Tipo.FLOAT && origem == Tipo.INTER) {
            return;   // alargamento: 3 vira 3.0 sem perda
        }
        if (destino == Tipo.INTER && origem == Tipo.FLOAT) {
            erro("R5", valor, "%s é padoINTER e não pode receber um padoFLOAT (a parte "
                    + "decimal seria perdida; para truncar, use padoINTER(valor))", oQue);
            return;
        }
        erro(codigo, valor, "%s é %s e não pode receber um %s",
                oQue, destino.palavra(), origem.palavra());
    }

    /** Tipo de uma expressão, ou null se ela tiver algum erro (já registrado). */
    private Tipo tipoDe(Expressao e) {
        Tipo t = calcularTipo(e);
        if (t != null) {
            anotacoes.registrar(e, t);
        }
        return t;
    }

    private Tipo calcularTipo(Expressao e) {
        return switch (e) {
            case Expressao.LiteralInteiro i -> {
                // R10: o literal precisa caber em um int de 32 bits
                if (new BigInteger(i.lexema()).compareTo(MAIOR_INTER) > 0) {
                    erro("R10", i, "o literal inteiro %s não cabe em padoINTER (máximo %d)",
                            i.lexema(), Integer.MAX_VALUE);
                }
                yield Tipo.INTER;
            }
            case Expressao.LiteralReal r -> {
                if (Double.isInfinite(Double.parseDouble(r.lexema()))) {
                    erro("R10", r, "o literal real %s não cabe em padoFLOAT", r.lexema());
                }
                yield Tipo.FLOAT;
            }
            case Expressao.LiteralTexto t -> Tipo.STRING;
            case Expressao.LiteralLogico l -> Tipo.OLEAN;
            case Expressao.Variavel v -> {
                Simbolo s = variavel(v.nome(), v.linha(), v.coluna());
                yield s == null ? null : s.tipo();
            }
            case Expressao.Unaria u -> unaria(u);
            case Expressao.Chamada ch -> chamada(ch);
            case Expressao.ConversaoInter c -> {
                // R19: padoINTER(x) converte números; texto e lógico não têm valor inteiro
                Tipo t = tipoDe(c.valor());
                if (t != null && !t.numerico()) {
                    erro("R19", c, "padoINTER(...) converte padoFLOAT ou padoINTER, não %s",
                            t.palavra());
                    yield null;
                }
                yield t == null ? null : Tipo.INTER;
            }
            case Expressao.Binaria b -> binaria(b);
        };
    }

    /** R6 (menos unário) e R17 (padNOT). */
    private Tipo unaria(Expressao.Unaria u) {
        Tipo t = tipoDe(u.operando());
        if (t == null) {
            return null;
        }
        if (u.operador() == OperadorUnario.NEGATIVO && !t.numerico()) {
            erro("R6", u, "o menos unário só se aplica a padoINTER ou padoFLOAT, não a %s", t.palavra());
            return null;
        }
        if (u.operador() == OperadorUnario.NAO && t != Tipo.OLEAN) {
            erro("R17", u, "padNOT só se aplica a padoOLEAN, não a %s", t.palavra());
            return null;
        }
        return t;
    }

    private Tipo binaria(Expressao.Binaria b) {
        Tipo esq = tipoDe(b.esquerda());
        Tipo dir = tipoDe(b.direita());
        Operador op = b.operador();

        // R14: divisão (ou resto) por um zero escrito no código
        if ((op == Operador.DIVISAO || op == Operador.MODULO) && zeroLiteral(b.direita())) {
            erro("R14", b, "divisão por zero");
        }
        if (esq == null || dir == null) {
            return null;
        }

        return switch (op.categoria) {
            case ARITMETICO -> aritmetica(b, esq, dir);
            case RELACIONAL -> {
                // R7: ordem só entre números ou entre textos
                if ((esq.numerico() && dir.numerico()) || (esq == Tipo.STRING && dir == Tipo.STRING)) {
                    yield Tipo.OLEAN;
                }
                if (esq == Tipo.OLEAN && dir == Tipo.OLEAN) {
                    erro("R7", b, "o operador '%s' não se aplica a padoOLEAN (lógicos só são "
                            + "comparados com == e !=)", op.simbolo);
                } else {
                    erro("R7", b, "não é possível comparar %s com %s usando '%s'",
                            esq.palavra(), dir.palavra(), op.simbolo);
                }
                yield null;
            }
            case IGUALDADE -> {
                // R7: igualdade entre dois números, dois textos ou dois lógicos
                if ((esq.numerico() && dir.numerico()) || esq == dir) {
                    yield Tipo.OLEAN;
                }
                erro("R7", b, "não é possível comparar %s com %s usando '%s'",
                        esq.palavra(), dir.palavra(), op.simbolo);
                yield null;
            }
            case LOGICO -> {
                // R17: padAND e padOR só entre lógicos
                if (esq == Tipo.OLEAN && dir == Tipo.OLEAN) {
                    yield Tipo.OLEAN;
                }
                erro("R17", b, "%s só se aplica a padoOLEAN, mas recebeu %s e %s",
                        op.simbolo, esq.palavra(), dir.palavra());
                yield null;
            }
        };
    }

    /** R6: + - * / entre números; + com texto concatena; R18: padoMOD só entre inteiros. */
    private Tipo aritmetica(Expressao.Binaria b, Tipo esq, Tipo dir) {
        Operador op = b.operador();
        if (op == Operador.SOMA && (esq == Tipo.STRING || dir == Tipo.STRING)) {
            return Tipo.STRING;   // concatenação: o outro lado vira texto, como no Java
        }
        if (op == Operador.MODULO) {
            if (esq == Tipo.INTER && dir == Tipo.INTER) {
                return Tipo.INTER;
            }
            erro("R18", b, "padoMOD só se aplica a padoINTER, mas recebeu %s e %s",
                    esq.palavra(), dir.palavra());
            return null;
        }
        if (esq.numerico() && dir.numerico()) {
            return (esq == Tipo.INTER && dir == Tipo.INTER) ? Tipo.INTER : Tipo.FLOAT;
        }
        erro("R6", b, "operação '%s' inválida entre %s e %s (aritmética só com padoINTER e "
                + "padoFLOAT%s)", op.simbolo, esq.palavra(), dir.palavra(),
                op == Operador.SOMA ? ", ou + com um padoSTRING" : "");
        return null;
    }

    private static boolean zeroLiteral(Expressao e) {
        return switch (e) {
            case Expressao.LiteralInteiro i -> new BigInteger(i.lexema()).signum() == 0;
            case Expressao.LiteralReal r -> Double.parseDouble(r.lexema()) == 0.0;
            case Expressao.Unaria u when u.operador() == OperadorUnario.NEGATIVO -> zeroLiteral(u.operando());
            default -> false;
        };
    }

    /**
     * R8: a função existe, recebe o número certo de argumentos, e cada um é compatível.
     * R15: chamar uma variável como se fosse função é erro.
     */
    private Tipo chamada(Expressao.Chamada ch) {
        List<Tipo> tiposArgs = new ArrayList<>();
        for (Expressao arg : ch.argumentos()) {
            tiposArgs.add(tipoDe(arg));
        }

        Funcao f = funcoes.get(ch.nome());
        if (f == null) {
            if (simbolos.buscar(ch.nome()) != null) {
                erro("R15", ch, "'%s' é uma variável, não uma função", ch.nome());
            } else {
                erro("R8", ch, "função '%s' não declarada", ch.nome());
            }
            return null;
        }

        List<Funcao.Parametro> params = f.parametros();
        if (params.size() != tiposArgs.size()) {
            erro("R8", ch, "a função '%s' espera %d argumento(s), mas recebeu %d",
                    f.nome(), params.size(), tiposArgs.size());
        } else {
            for (int i = 0; i < params.size(); i++) {
                Funcao.Parametro p = params.get(i);
                compatibilizar("R8", p.tipo(), tiposArgs.get(i), ch.argumentos().get(i),
                        "o argumento " + (i + 1) + " de '" + f.nome() + "' (parâmetro '" + p.nome() + "')");
            }
        }
        return f.tipoRetorno();   // mesmo com argumentos errados, o tipo do resultado é conhecido
    }

    // ------------------------------------------------------------ erros

    /** codigo é a regra violada (R1..R21); o erro aponta para o trecho do nó. */
    private void erro(String codigo, Expressao no, String formato, Object... args) {
        erro(codigo, no.linha(), no.coluna(), tamanhoDe(no), formato, args);
    }

    private void erro(String codigo, Comando no, String formato, Object... args) {
        erro(codigo, no.linha(), no.coluna(), tamanhoDe(no), formato, args);
    }

    private void erro(String codigo, Funcao f, String formato, Object... args) {
        erro(codigo, f.linha(), f.coluna(), tamanho(f.nome()), formato, args);
    }

    private void erro(String codigo, int linha, int coluna, int tamanho, String formato, Object... args) {
        erros.add(Diagnostico.erro(Fase.SEMANTICO, codigo, linha, coluna, tamanho, String.format(formato, args)));
    }

    /**
     * Tamanho, em code points, do trecho que o nó ocupa a partir da sua posição: o nome, o operador,
     * o literal ou a palavra reservada, conforme o que a posição do nó aponta (ver Comando e Expressao).
     */
    private static int tamanhoDe(Expressao e) {
        return switch (e) {
            case Expressao.Binaria b -> tamanho(b.operador().simbolo);
            case Expressao.Unaria u -> tamanho(u.operador().simbolo);
            case Expressao.Variavel v -> tamanho(v.nome());
            case Expressao.Chamada ch -> tamanho(ch.nome());
            case Expressao.ConversaoInter c -> tamanho("padoINTER");
            case Expressao.LiteralInteiro i -> tamanho(i.lexema());
            case Expressao.LiteralReal r -> tamanho(r.lexema());
            case Expressao.LiteralTexto t -> tamanho(t.lexema());
            case Expressao.LiteralLogico l -> tamanho(l.valor() ? "padoTRUE" : "padoFALSE");
        };
    }

    private static int tamanhoDe(Comando c) {
        return switch (c) {
            case Comando.Declaracao d -> tamanho(d.nome());
            case Comando.Atribuicao a -> tamanho(a.nome());
            case Comando.Incremento inc -> tamanho(inc.nome());
            case Comando.Leitura l -> tamanho(l.nome());
            case Comando.ChamadaComando ch -> tamanho(ch.chamada().nome());
            case Comando.Escrita e -> tamanho(e.pulaLinha() ? "padoPRINTLN" : "padoPRINT");
            case Comando.Se se -> tamanho("pad");
            case Comando.Repeticao rep -> tamanho("papapadoin");
            case Comando.Enquanto enq -> tamanho("padoinquanto");
            case Comando.Retorno r -> tamanho("padOUT");
            case Comando.Interrompe i -> tamanho("padBREAK");
            case Comando.Prossegue p -> tamanho("padCONTINUE");
        };
    }

    private static int tamanho(String texto) {
        return texto.codePointCount(0, texto.length());
    }
}

package padolang.semantica;

import padolang.ast.Comando;
import padolang.ast.Comparacao;
import padolang.ast.Expressao;
import padolang.ast.Funcao;
import padolang.ast.OpAritmetico;
import padolang.ast.OpRelacional;
import padolang.ast.Programa;
import padolang.ast.Tipo;
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
 * As regras estão numeradas (R1..R15) como em docs/semantica.md. Regras de tipo:
 *   - atribuir, inicializar, passar argumento e retornar seguem a mesma regra de compatibilidade:
 *     mesmo tipo, ou padoINTER -> padoFLOAT (alargamento). padoFLOAT -> padoINTER é ERRO,
 *     porque a parte decimal seria perdida sem aviso;
 *   - aritmética (+ - * / e menos unário) só com números: INTER op INTER = INTER, senão FLOAT;
 *   - comparação: número com número, texto com texto (qualquer operador) e lógico com lógico
 *     (só == e !=).
 *
 * Quando uma expressão já tem erro, seu tipo vira null ("desconhecido") e as verificações que
 * dependem dele são puladas. Assim um único erro não gera uma cascata de mensagens.
 */
public final class AnalisadorSemantico {

    private static final BigInteger MAIOR_INTER = BigInteger.valueOf(Integer.MAX_VALUE);

    private final Map<String, Funcao> funcoes = new LinkedHashMap<>();
    private final TabelaDeSimbolos simbolos = new TabelaDeSimbolos();
    private final List<String> erros = new ArrayList<>();

    /** Função sendo analisada; null enquanto analisamos o PADOIN. */
    private Funcao funcaoAtual;

    /** Nomes declarados no nível mais externo do PADOIN, só para melhorar uma mensagem de erro. */
    private final Set<String> variaveisDoPrincipal = new HashSet<>();

    public void analisar(Programa programa) {
        // 1. Tabela de funções (R9: nomes de função não se repetem)
        for (Funcao f : programa.funcoes()) {
            Funcao anterior = funcoes.putIfAbsent(f.nome(), f);
            if (anterior != null) {
                erro(f.linha(), "a função '%s' já foi declarada na linha %d", f.nome(), anterior.linha());
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
    }

    // ------------------------------------------------------------ funções

    private void funcao(Funcao f) {
        funcaoAtual = f;
        // Parâmetros e corpo da função ficam no MESMO escopo (como em C e Java):
        // declarar no corpo uma variável com o nome de um parâmetro é redeclaração.
        simbolos.abrirEscopo();
        for (Funcao.Parametro p : f.parametros()) {
            declarar(p.nome(), p.tipo(), p.linha(), true);
        }
        comandos(f.corpo());
        simbolos.fecharEscopo();

        // R12: toda função devolve um valor, então todo caminho precisa terminar em padOUT
        if (!sempreRetorna(f.corpo())) {
            erro(f.linha(), "a função '%s' pode terminar sem padOUT: todo caminho precisa devolver um %s",
                    f.nome(), f.tipoRetorno().palavra());
        }
        funcaoAtual = null;
    }

    /**
     * Um bloco sempre retorna se algum comando dele sempre retorna:
     *   - padOUT sempre retorna;
     *   - pad/oin sempre retorna se tiver 'oin' e os DOIS ramos sempre retornarem;
     *   - papapadoin nunca conta, porque o corpo pode não executar nenhuma vez.
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

    // ------------------------------------------------------------ comandos

    /** Cada { } abre um escopo novo; o que for declarado dentro some ao fechar. */
    private void bloco(List<Comando> lista) {
        simbolos.abrirEscopo();
        comandos(lista);
        simbolos.fecharEscopo();
    }

    private void comandos(List<Comando> lista) {
        for (Comando c : lista) {
            comando(c);
        }
    }

    private void comando(Comando c) {
        switch (c) {
            case Comando.Declaracao d -> {
                // O valor inicial é analisado ANTES de declarar: "padoin padoINTER x = x + 1👍" é erro
                if (d.inicial() != null) {
                    Tipo t = tipoDe(d.inicial());
                    compatibilizar(d.tipo(), t, d.linha(), "a variável '" + d.nome() + "'");
                }
                declarar(d.nome(), d.tipo(), d.linha(), false);
            }
            case Comando.Atribuicao a -> {
                Simbolo s = variavel(a.nome(), a.linha());
                Tipo t = tipoDe(a.valor());
                if (s != null) {
                    compatibilizar(s.tipo(), t, a.linha(), "a variável '" + a.nome() + "'");
                }
            }
            case Comando.Incremento inc -> {
                Simbolo s = variavel(inc.nome(), inc.linha());
                if (s != null && !s.tipo().numerico()) {           // R13
                    erro(inc.linha(), "'%s' só se aplica a padoINTER ou padoFLOAT, mas '%s' é %s",
                            inc.decremento() ? "--" : "++", inc.nome(), s.tipo().palavra());
                }
            }
            case Comando.Leitura l -> variavel(l.nome(), l.linha());
            case Comando.Escrita e -> tipoDe(e.valor());
            case Comando.Retorno r -> {
                Tipo t = tipoDe(r.valor());
                if (funcaoAtual == null) {                         // R11
                    erro(r.linha(), "padOUT fora de função: o bloco PADOIN não devolve valor");
                } else {
                    compatibilizar(funcaoAtual.tipoRetorno(), t, r.linha(),
                            "o retorno da função '" + funcaoAtual.nome() + "'");
                }
            }
            case Comando.Se se -> {
                comparacao(se.condicao());
                bloco(se.entao());
                if (se.senao() != null) {
                    bloco(se.senao());
                }
            }
            case Comando.Repeticao rep -> {
                if (rep.inicio() != null) {
                    comando(rep.inicio());
                }
                comparacao(rep.condicao());
                if (rep.passo() != null) {
                    comando(rep.passo());
                }
                bloco(rep.corpo());
            }
        }
    }

    // ------------------------------------------------------------ declarações e nomes

    private void declarar(String nome, Tipo tipo, int linha, boolean parametro) {
        String oQue = parametro ? "parâmetro" : "variável";

        // R3: uma variável não pode ter o nome de uma função
        if (funcoes.containsKey(nome)) {
            erro(linha, "'%s' já é o nome de uma função e não pode ser usado como %s", nome, oQue);
        }

        // R2: não se declara de novo um nome que ainda está visível (mesmo escopo ou escopo externo)
        Simbolo existente = simbolos.buscar(nome);
        if (existente != null) {
            erro(linha, "'%s' já foi declarado como %s na linha %d", nome,
                    existente.parametro() ? "parâmetro" : "variável", existente.linha());
            return;   // mantém a primeira declaração
        }
        simbolos.declarar(new Simbolo(nome, tipo, linha, parametro));
    }

    /**
     * R1: todo nome usado como variável precisa estar declarado e visível.
     * R15: se o nome for de uma função, a mensagem avisa que faltou chamá-la.
     */
    private Simbolo variavel(String nome, int linha) {
        Simbolo s = simbolos.buscar(nome);
        if (s != null) {
            return s;
        }
        if (funcoes.containsKey(nome)) {
            erro(linha, "'%s' é uma função, não uma variável (para chamá-la use %s(...))", nome, nome);
        } else if (funcaoAtual != null && variaveisDoPrincipal.contains(nome)) {
            erro(linha, "variável '%s' não declarada na função '%s' (funções não enxergam as variáveis "
                    + "do PADOIN; passe o valor como parâmetro)", nome, funcaoAtual.nome());
        } else {
            erro(linha, "variável '%s' não declarada", nome);
        }
        return null;
    }

    // ------------------------------------------------------------ tipos

    /**
     * R4/R5: compatibilidade de atribuição, usada em declaração, atribuição, argumento e padOUT.
     * Aceita o mesmo tipo ou padoINTER -> padoFLOAT.
     */
    private void compatibilizar(Tipo destino, Tipo origem, int linha, String oQue) {
        if (origem == null || destino == origem) {
            return;
        }
        if (destino == Tipo.FLOAT && origem == Tipo.INTER) {
            return;   // alargamento: 3 vira 3.0 sem perda
        }
        if (destino == Tipo.INTER && origem == Tipo.FLOAT) {
            erro(linha, "%s é padoINTER e não pode receber um padoFLOAT (a parte decimal seria perdida)", oQue);
            return;
        }
        erro(linha, "%s é %s e não pode receber um %s", oQue, destino.palavra(), origem.palavra());
    }

    /** Tipo de uma expressão, ou null se ela tiver algum erro (já registrado). */
    private Tipo tipoDe(Expressao e) {
        return switch (e) {
            case Expressao.LiteralInteiro i -> {
                // R10: o literal precisa caber em um int de 32 bits
                if (new BigInteger(i.lexema()).compareTo(MAIOR_INTER) > 0) {
                    erro(i.linha(), "o literal inteiro %s não cabe em padoINTER (máximo %d)",
                            i.lexema(), Integer.MAX_VALUE);
                }
                yield Tipo.INTER;
            }
            case Expressao.LiteralReal r -> {
                if (Double.isInfinite(Double.parseDouble(r.lexema()))) {
                    erro(r.linha(), "o literal real %s não cabe em padoFLOAT", r.lexema());
                }
                yield Tipo.FLOAT;
            }
            case Expressao.LiteralTexto t -> Tipo.STRING;
            case Expressao.LiteralLogico l -> Tipo.OLEAN;
            case Expressao.Variavel v -> {
                Simbolo s = variavel(v.nome(), v.linha());
                yield s == null ? null : s.tipo();
            }
            case Expressao.Negacao n -> {
                Tipo t = tipoDe(n.operando());
                if (t != null && !t.numerico()) {
                    erro(n.linha(), "o menos unário só se aplica a padoINTER ou padoFLOAT, não a %s", t.palavra());
                    yield null;
                }
                yield t;
            }
            case Expressao.Chamada ch -> chamada(ch);
            case Expressao.Binaria b -> binaria(b);
        };
    }

    /** R6/R14: aritmética só entre números, e sem divisão por um zero literal. */
    private Tipo binaria(Expressao.Binaria b) {
        Tipo esq = tipoDe(b.esquerda());
        Tipo dir = tipoDe(b.direita());

        if (b.operador() == OpAritmetico.DIVISAO && zeroLiteral(b.direita())) {
            erro(b.linha(), "divisão por zero");
        }
        if (esq == null || dir == null) {
            return null;
        }
        if (esq.numerico() && dir.numerico()) {
            return (esq == Tipo.INTER && dir == Tipo.INTER) ? Tipo.INTER : Tipo.FLOAT;
        }
        erro(b.linha(), "operação '%s' inválida entre %s e %s (aritmética só com padoINTER e padoFLOAT)",
                b.operador().simbolo, esq.palavra(), dir.palavra());
        return null;
    }

    private static boolean zeroLiteral(Expressao e) {
        return switch (e) {
            case Expressao.LiteralInteiro i -> new BigInteger(i.lexema()).signum() == 0;
            case Expressao.LiteralReal r -> Double.parseDouble(r.lexema()) == 0.0;
            case Expressao.Negacao n -> zeroLiteral(n.operando());
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
                erro(ch.linha(), "'%s' é uma variável, não uma função", ch.nome());
            } else {
                erro(ch.linha(), "função '%s' não declarada", ch.nome());
            }
            return null;
        }

        List<Funcao.Parametro> params = f.parametros();
        if (params.size() != tiposArgs.size()) {
            erro(ch.linha(), "a função '%s' espera %d argumento(s), mas recebeu %d",
                    f.nome(), params.size(), tiposArgs.size());
        } else {
            for (int i = 0; i < params.size(); i++) {
                Funcao.Parametro p = params.get(i);
                compatibilizar(p.tipo(), tiposArgs.get(i), ch.linha(),
                        "o argumento " + (i + 1) + " de '" + f.nome() + "' (parâmetro '" + p.nome() + "')");
            }
        }
        return f.tipoRetorno();   // mesmo com argumentos errados, o tipo do resultado é conhecido
    }

    /** R7: os dois lados da comparação precisam ser do mesmo "tipo de coisa". */
    private void comparacao(Comparacao c) {
        Tipo esq = tipoDe(c.esquerda());
        Tipo dir = tipoDe(c.direita());
        if (esq == null || dir == null) {
            return;
        }
        String op = c.operador().simbolo;

        if (esq.numerico() && dir.numerico()) {
            return;
        }
        if (esq == Tipo.STRING && dir == Tipo.STRING) {
            return;   // ordem alfabética (strcmp no C)
        }
        if (esq == Tipo.OLEAN && dir == Tipo.OLEAN) {
            if (c.operador() != OpRelacional.IGUAL && c.operador() != OpRelacional.DIFERENTE) {
                erro(c.linha(), "o operador '%s' não se aplica a padoOLEAN (lógicos só são comparados com == e !=)", op);
            }
            return;
        }
        erro(c.linha(), "não é possível comparar %s com %s usando '%s'", esq.palavra(), dir.palavra(), op);
    }

    // ------------------------------------------------------------ erros

    private void erro(int linha, String formato, Object... args) {
        erros.add(String.format("padoERRO semântico (linha %d): %s", linha, String.format(formato, args)));
    }
}

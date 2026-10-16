package padolang.ast;

import java.util.List;

/** Raiz da AST: funções declaradas antes do bloco PADOIN + corpo principal. */
public record Programa(List<Funcao> funcoes, List<Comando> principal) {
}

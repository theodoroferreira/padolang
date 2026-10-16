# Declaração de uso de IA generativa

**Atividade:** Estudo Independente 03 — Analisador léxico da Padolang
**Componente curricular:** Linguagens Formais e Compiladores
**Autores:** Theodoro Gaspar Ferreira e Vinícius Andrey Costa Mattos

Declaramos que utilizamos inteligência artificial generativa na elaboração deste trabalho, da forma descrita a seguir.

## Ferramenta

Claude Code (Anthropic), com o modelo Claude Opus 5.5, usado como assistente de programação e de escrita dentro do repositório do compilador da Padolang.

## Em que a ferramenta foi usada

1. **Código-fonte.** Para esta atividade, a ferramenta implementou o comentário de bloco (`#* ... *#`, com o erro `L7` quando não é fechado), o erro único para números com mais de um ponto decimal (`1.2.3`), a exibição do lexema em cada erro da listagem de tokens e os ajustes que o comentário de bloco exigiu no formatador, no servidor LSP e nas gramáticas dos editores.
2. **Casos de teste.** A ferramenta escreveu os arquivos de teste de `testes/lexer_editor/` criados para esta atividade (`todos_os_tokens`, `erros_lexicos`, `comentario_de_bloco` e `comentario_nao_fechado`) e gerou as saídas esperadas executando o analisador.
3. **Relatório.** A ferramenta redigiu a primeira versão do texto do relatório, as tabelas e os diagramas de transição, com base no código, nas decisões de projeto registradas no repositório e na bibliografia indicada no enunciado.

## Como o resultado foi conferido

- Todas as listagens de saída do relatório foram produzidas pela execução real do analisador, e não escritas à mão.
- As suítes de teste do repositório foram executadas depois das alterações e passam (`testar.sh`, `testar-lexer-editor.sh`, `testar-cli.sh`, `testar-formatador.sh`, `testar-lsp.sh` e os testes das gramáticas dos editores).
- A especificação em JFlex foi executada (JFlex 1.9.1) e comparada com o analisador escrito à mão em todos os programas do repositório e em 300 000 entradas aleatórias, sem diferenças.

## Responsabilidade

Os autores leram e revisaram o código, os testes e o texto produzidos com a ajuda da ferramenta, conferiram as citações na bibliografia e respondem pelo conteúdo entregue, inclusive por eventuais erros.

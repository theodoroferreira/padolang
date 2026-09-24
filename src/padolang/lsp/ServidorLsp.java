package padolang.lsp;

import padolang.cli.Cli;
import padolang.diagnostico.CompilacaoException;
import padolang.diagnostico.Diagnostico;
import padolang.diagnostico.Severidade;
import padolang.formatador.Formatador;
import padolang.lexer.Lexer;
import padolang.lexer.Token;
import padolang.lexer.TokenType;
import padolang.lsp.json.Json;
import padolang.lsp.json.JsonException;
import padolang.parser.Parser;
import padolang.semantica.AnalisadorSemantico;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servidor LSP da padolang (padolang lsp), por stdin/stdout, sem dependências.
 *
 * Métodos atendidos:
 *   initialize, initialized, shutdown, exit           ciclo de vida
 *   textDocument/didOpen, didChange, didClose         texto inteiro a cada mudança (textDocumentSync = 1)
 *   textDocument/publishDiagnostics                   enviado depois de cada análise
 *   textDocument/semanticTokens/full                  cores (TokensSemanticos)
 *   textDocument/formatting                           o Formatador, como um edit do documento inteiro
 *
 * Toda requisição roda num try/catch: um erro vira resposta de erro JSON-RPC e o servidor continua.
 * Requisição desconhecida responde MethodNotFound (-32601); notificação desconhecida é ignorada.
 * O log vai para o stderr: o stdout é só do protocolo.
 */
public final class ServidorLsp {

    // códigos de erro do JSON-RPC
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;
    private static final int SERVER_NOT_INITIALIZED = -32002;

    private final JsonRpc rpc;
    private final PrintStream log;
    private final Map<String, String> documentos = new HashMap<>();   // uri -> texto
    private boolean inicializado = false;
    private boolean desligando = false;                                // recebeu shutdown

    public ServidorLsp(InputStream entrada, OutputStream saida, PrintStream log) {
        this.rpc = new JsonRpc(entrada, saida);
        this.log = log;
    }

    /** Atende mensagens até o exit (ou o fim da entrada). Devolve o código de saída do processo. */
    public int executar() {
        log.println("padolang lsp " + Cli.VERSAO + ": aguardando mensagens no stdin");
        while (true) {
            String corpo;
            try {
                corpo = rpc.ler();
            } catch (IOException e) {
                log.println("padolang lsp: " + e.getMessage());
                return 1;
            }
            if (corpo == null) {                     // o cliente fechou a entrada sem mandar exit
                return desligando ? 0 : 1;
            }
            Integer saida = atender(corpo);
            if (saida != null) return saida;
        }
    }

    /** Trata uma mensagem. Devolve o código de saída se ela for o exit; senão null. */
    private Integer atender(String corpo) {
        Object mensagem;
        try {
            mensagem = Json.ler(corpo);
        } catch (JsonException e) {
            responderErro(null, PARSE_ERROR, "JSON inválido: " + e.getMessage());
            return null;
        }
        if (!(mensagem instanceof Map<?, ?>)) {
            responderErro(null, INVALID_REQUEST, "a mensagem precisa ser um objeto JSON");
            return null;
        }

        Map<?, ?> campos = (Map<?, ?>) mensagem;
        Object id = campos.get("id");
        boolean requisicao = campos.containsKey("id");
        if (!(campos.get("method") instanceof String nome)) {
            boolean resposta = campos.containsKey("result") || campos.containsKey("error");
            if (requisicao && !resposta) {
                responderErro(id, INVALID_REQUEST, "mensagem sem 'method'");
            }
            return null;                             // resposta a uma requisição nossa: nada a fazer
        }
        Object params = Json.campo(mensagem, "params");

        if (nome.equals("exit")) {
            return desligando ? 0 : 1;
        }
        try {
            if (!inicializado && !nome.equals("initialize")) {
                if (requisicao) responderErro(id, SERVER_NOT_INITIALIZED, "o servidor ainda não recebeu initialize");
                return null;
            }
            if (desligando && requisicao) {
                responderErro(id, INVALID_REQUEST, "o servidor já recebeu shutdown");
                return null;
            }
            switch (nome) {
                case "initialize" -> {
                    inicializado = true;
                    responder(id, inicializar());
                }
                case "initialized", "$/cancelRequest", "$/setTrace" -> { }
                case "shutdown" -> {
                    desligando = true;
                    responder(id, null);
                }
                case "textDocument/didOpen" -> {
                    String uri = Json.texto(params, "textDocument", "uri");
                    documentos.put(uri, Json.texto(params, "textDocument", "text"));
                    publicarDiagnosticos(uri);
                }
                case "textDocument/didChange" -> {
                    String uri = Json.texto(params, "textDocument", "uri");
                    Object mudancas = Json.campo(params, "contentChanges");
                    if (mudancas instanceof List<?> lista && !lista.isEmpty()) {
                        // sincronização do texto inteiro: a última mudança é o documento todo
                        documentos.put(uri, Json.texto(lista.get(lista.size() - 1), "text"));
                        publicarDiagnosticos(uri);
                    }
                }
                case "textDocument/didClose" -> {
                    String uri = Json.texto(params, "textDocument", "uri");
                    documentos.remove(uri);
                    notificar("textDocument/publishDiagnostics",
                            Json.objeto("uri", uri, "diagnostics", List.of()));
                }
                case "textDocument/semanticTokens/full" -> {
                    String texto = documento(params);
                    responder(id, Json.objeto("data", TokensSemanticos.codificar(texto)));
                }
                case "textDocument/formatting" -> responder(id, formatar(documento(params)));
                default -> {
                    if (requisicao) responderErro(id, METHOD_NOT_FOUND, "método não suportado: " + nome);
                }
            }
        } catch (IllegalArgumentException | JsonException e) {   // parâmetros faltando ou errados
            log.println("padolang lsp: " + nome + ": " + e.getMessage());
            if (requisicao) responderErro(id, INVALID_PARAMS, nome + ": " + e.getMessage());
        } catch (Exception e) {
            log.println("padolang lsp: erro em " + nome + ": " + e);
            if (requisicao) responderErro(id, INTERNAL_ERROR, nome + ": " + e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------ métodos

    private Map<String, Object> inicializar() {
        Map<String, Object> capacidades = Json.objeto(
                "textDocumentSync", 1,
                "semanticTokensProvider", Json.objeto(
                        "legend", Json.objeto(
                                "tokenTypes", TokensSemanticos.TIPOS,
                                "tokenModifiers", TokensSemanticos.MODIFICADORES),
                        "full", true),
                "documentFormattingProvider", true);
        return Json.objeto(
                "capabilities", capacidades,
                "serverInfo", Json.objeto("name", "padolang", "version", Cli.VERSAO));
    }

    /** Um único edit que troca o documento inteiro; lista vazia se já está formatado ou tem erro. */
    private List<Object> formatar(String texto) {
        String formatado;
        try {
            formatado = Formatador.formatar(texto);
        } catch (CompilacaoException e) {
            return List.of();                        // o erro já aparece nos diagnósticos
        }
        if (formatado.equals(texto)) return List.of();
        Posicoes posicoes = new Posicoes(texto);
        Map<String, Object> tudo = Json.objeto(
                "start", Json.objeto("line", 0, "character", 0),
                "end", posicoes.fimDoDocumento());
        return List.of(Json.objeto("range", tudo, "newText", formatado));
    }

    private void publicarDiagnosticos(String uri) {
        String texto = documentos.get(uri);
        Posicoes posicoes = new Posicoes(texto);
        List<Object> lista = new ArrayList<>();
        for (Diagnostico d : diagnosticar(texto)) {
            lista.add(Json.objeto(
                    "range", posicoes.intervalo(d.linha(), d.coluna(), d.tamanho()),
                    "severity", d.severidade() == Severidade.ERRO ? 1 : 2,
                    "code", d.codigo(),
                    "source", "padolang",
                    "message", d.mensagem()));
        }
        notificar("textDocument/publishDiagnostics", Json.objeto("uri", uri, "diagnostics", lista));
    }

    /**
     * Léxico (modo editor, todos os erros) -> sintático (o primeiro erro) -> semântico (todos).
     * Cada etapa só roda se a anterior não achou erro: um erro léxico produziria erros sintáticos
     * falsos logo em seguida, e a semântica precisa de uma AST completa.
     */
    public static List<Diagnostico> diagnosticar(String texto) {
        Lexer lexer = new Lexer(texto, Lexer.Modo.EDITOR);
        List<Token> tokens = lexer.tokenizar();
        if (!lexer.erros().isEmpty()) return lexer.erros();
        try {
            List<Token> codigo = tokens.stream().filter(t -> t.tipo() != TokenType.COMENTARIO).toList();
            new AnalisadorSemantico().analisar(new Parser(codigo).programa());
            return List.of();
        } catch (CompilacaoException e) {
            return e.diagnosticos();
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private String documento(Object params) {
        String uri = Json.texto(params, "textDocument", "uri");
        String texto = documentos.get(uri);
        if (texto == null) throw new IllegalArgumentException("documento não aberto: " + uri);
        return texto;
    }

    private void responder(Object id, Object resultado) {
        Map<String, Object> resposta = Json.objeto("jsonrpc", "2.0", "id", id);
        resposta.put("result", resultado);
        enviar(resposta);
    }

    private void responderErro(Object id, int codigo, String mensagem) {
        enviar(Json.objeto("jsonrpc", "2.0", "id", id,
                "error", Json.objeto("code", codigo, "message", mensagem)));
    }

    private void notificar(String metodo, Object params) {
        enviar(Json.objeto("jsonrpc", "2.0", "method", metodo, "params", params));
    }

    private void enviar(Object mensagem) {
        try {
            rpc.escrever(mensagem);
        } catch (IOException e) {
            log.println("padolang lsp: falha ao escrever no stdout: " + e.getMessage());
        }
    }
}

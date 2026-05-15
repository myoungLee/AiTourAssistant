/*
 * @author myoung
 */
package com.aitour.client.mcp.external;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.config.mcp.McpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证外部 MCP 适配器会按真实 HTTP/JSON-RPC 生命周期完成初始化、工具发现和工具调用。
 *
 * @author myoung
 */
class ExternalMcpToolAdapterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    /**
     * 测试结束后关闭本地模拟 MCP Server，避免端口和线程泄漏到后续用例。
     */
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 外部模式下应完成 initialize、notifications/initialized、tools/list、tools/call 全流程。
     */
    @Test
    void shouldCallExternalMcpServerThroughLifecycle() throws Exception {
        RecordingMcpHandler handler = new RecordingMcpHandler();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", handler);
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
        McpProperties properties = new McpProperties("external", new McpProperties.External(baseUrl, 3));
        ExternalMcpToolAdapter adapter = new ExternalMcpToolAdapter(RestClient.builder().build(), properties, "weather.query");

        assertThat(adapter.listAvailableToolNames()).containsExactly("weather.query");

        ToolResult result = adapter.execute(new ToolRequest(1L, 2L, Map.of("city", "成都")));

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("成都天气晴朗");
        assertThat(result.data()).containsEntry("city", "成都");
        assertThat(handler.methods()).containsExactly(
                "initialize",
                "notifications/initialized",
                "tools/list",
                "tools/call"
        );
        assertThat(handler.protocolHeaders()).containsExactly(
                null,
                "2025-06-18",
                "2025-06-18",
                "2025-06-18"
        );
        assertThat(handler.sessionHeaders()).containsExactly(
                null,
                "session-123",
                "session-123",
                "session-123"
        );
    }

    /**
     * 轻量模拟 MCP Server，记录请求顺序并按协议返回固定响应，供测试验证。
     *
     * @author myoung
     */
    private static final class RecordingMcpHandler implements HttpHandler {
        private final List<String> methods = new CopyOnWriteArrayList<>();
        private final List<String> protocolHeaders = new CopyOnWriteArrayList<>();
        private final List<String> sessionHeaders = new CopyOnWriteArrayList<>();

        @Override
        /**
         * 按请求方法返回不同 MCP 响应，覆盖初始化、工具发现和工具调用阶段。
         */
        public void handle(HttpExchange exchange) throws IOException {
            JsonNode request = readRequest(exchange.getRequestBody());
            String method = request.path("method").asText();
            methods.add(method);
            protocolHeaders.add(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
            sessionHeaders.add(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));

            switch (method) {
                case "initialize" -> writeJson(exchange, 200, Map.of(
                        "jsonrpc", "2.0",
                        "id", request.path("id").asLong(),
                        "result", Map.of(
                                "protocolVersion", "2025-06-18",
                                "capabilities", Map.of(),
                                "serverInfo", Map.of("name", "mock-mcp", "version", "1.0.0")
                        )
                ), Map.of("Mcp-Session-Id", "session-123"));
                case "notifications/initialized" -> writePlain(exchange, 202, "", Map.of());
                case "tools/list" -> writeJson(exchange, 200, Map.of(
                        "jsonrpc", "2.0",
                        "id", request.path("id").asLong(),
                        "result", Map.of(
                                "tools", List.of(Map.of(
                                        "name", "weather.query",
                                        "description", "查询天气"
                                ))
                        )
                ), Map.of());
                case "tools/call" -> {
                    String city = request.path("params").path("arguments").path("city").asText("未知城市");
                    writeJson(exchange, 200, Map.of(
                            "jsonrpc", "2.0",
                            "id", request.path("id").asLong(),
                            "result", Map.of(
                                    "content", List.of(Map.of(
                                            "type", "text",
                                            "text", city + "天气晴朗，适合出行"
                                    )),
                                    "structuredContent", Map.of(
                                            "city", city,
                                            "condition", "晴朗"
                                    ),
                                    "isError", false
                            )
                    ), Map.of());
                }
                default -> writeJson(exchange, 400, Map.of(
                        "jsonrpc", "2.0",
                        "id", request.path("id").asLong(),
                        "error", Map.of(
                                "code", -32601,
                                "message", "unknown method"
                        )
                ), Map.of());
            }
        }

        /**
         * 返回请求方法调用顺序，供断言生命周期是否正确。
         */
        private List<String> methods() {
            return methods;
        }

        /**
         * 返回每次请求携带的协议版本头。
         */
        private List<String> protocolHeaders() {
            return protocolHeaders;
        }

        /**
         * 返回每次请求携带的会话头。
         */
        private List<String> sessionHeaders() {
            return sessionHeaders;
        }

        /**
         * 读取 JSON 请求体，避免在断言时依赖字符串拼接格式。
         */
        private JsonNode readRequest(InputStream inputStream) throws IOException {
            return OBJECT_MAPPER.readTree(inputStream);
        }

        /**
         * 输出 JSON 响应并支持附带响应头。
         */
        private void writeJson(HttpExchange exchange, int status, Map<String, Object> body, Map<String, String> headers) throws IOException {
            headers.forEach((key, value) -> exchange.getResponseHeaders().add(key, value));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(body);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }

        /**
         * 输出空文本响应，模拟 initialized 通知这类不返回 JSON-RPC 结果的场景。
         */
        private void writePlain(HttpExchange exchange, int status, String body, Map<String, String> headers) throws IOException {
            headers.forEach((key, value) -> exchange.getResponseHeaders().add(key, value));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}

/*
 * @author myoung
 */
package com.aitour.client.mcp.external;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.config.mcp.McpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证外部 MCP 适配器会按真实 HTTP/JSON-RPC 生命周期完成初始化、工具发现和工具调用。
 *
 * @author myoung
 */
class ExternalMcpToolAdapterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 外部模式下应完成 initialize、notifications/initialized、tools/list、tools/call 全流程。
     */
    @Test
    void shouldCallExternalMcpServerThroughLifecycle() throws Exception {
        String baseUrl = "https://mcp.example.test/mcp";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RecordedRequests recordedRequests = new RecordedRequests();
        prepareMcpLifecycleResponses(server, baseUrl, recordedRequests);

        McpProperties properties = new McpProperties("external", new McpProperties.External(baseUrl, 3));
        ExternalMcpToolAdapter adapter = new ExternalMcpToolAdapter(builder.build(), properties, "weather.query");

        assertThat(adapter.listAvailableToolNames()).containsExactly("weather.query");

        ToolResult result = adapter.execute(new ToolRequest(1L, 2L, Map.of("city", "成都")));

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("成都天气晴朗");
        assertThat(result.data()).containsEntry("city", "成都");
        assertThat(recordedRequests.methods()).containsExactly(
                "initialize",
                "notifications/initialized",
                "tools/list",
                "tools/call"
        );
        assertThat(recordedRequests.protocolHeaders()).containsExactly(
                null,
                "2025-06-18",
                "2025-06-18",
                "2025-06-18"
        );
        assertThat(recordedRequests.sessionHeaders()).containsExactly(
                null,
                "session-123",
                "session-123",
                "session-123"
        );
        server.verify();
    }

    /**
     * 准备 MCP 生命周期的 HTTP 响应，不启动本地端口，避免测试依赖 loopback。
     */
    private void prepareMcpLifecycleResponses(MockRestServiceServer server, String baseUrl, RecordedRequests recordedRequests) throws Exception {
        expectJsonRpc(server, baseUrl, recordedRequests, "initialize")
                .andRespond(withSuccess(json(Map.of(
                        "jsonrpc", "2.0",
                        "id", 1,
                        "result", Map.of(
                                "protocolVersion", "2025-06-18",
                                "capabilities", Map.of(),
                                "serverInfo", Map.of("name", "mock-mcp", "version", "1.0.0")
                        )
                )), MediaType.APPLICATION_JSON).header("Mcp-Session-Id", "session-123"));

        expectJsonRpc(server, baseUrl, recordedRequests, "notifications/initialized")
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        expectJsonRpc(server, baseUrl, recordedRequests, "tools/list")
                .andRespond(withSuccess(json(Map.of(
                        "jsonrpc", "2.0",
                        "id", 2,
                        "result", Map.of(
                                "tools", List.of(Map.of(
                                        "name", "weather.query",
                                        "description", "查询天气"
                                ))
                        )
                )), MediaType.APPLICATION_JSON));

        expectJsonRpc(server, baseUrl, recordedRequests, "tools/call")
                .andRespond(withSuccess(json(Map.of(
                        "jsonrpc", "2.0",
                        "id", 3,
                        "result", Map.of(
                                "content", List.of(Map.of(
                                        "type", "text",
                                        "text", "成都天气晴朗，适合出行"
                                )),
                                "structuredContent", Map.of(
                                        "city", "成都",
                                        "condition", "晴朗"
                                ),
                                "isError", false
                        )
                )), MediaType.APPLICATION_JSON));
    }

    /**
     * 注册一次 JSON-RPC 请求期望，并记录方法名和 MCP 关键请求头。
     */
    private org.springframework.test.web.client.ResponseActions expectJsonRpc(
            MockRestServiceServer server,
            String baseUrl,
            RecordedRequests recordedRequests,
            String expectedMethod
    ) {
        return server.expect(requestTo(baseUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    JsonNode requestBody = readRequest((MockClientHttpRequest) request);
                    String actualMethod = requestBody.path("method").asText();
                    recordedRequests.add(
                            actualMethod,
                            request.getHeaders().getFirst("MCP-Protocol-Version"),
                            request.getHeaders().getFirst("Mcp-Session-Id")
                    );
                    assertThat(actualMethod).isEqualTo(expectedMethod);
                });
    }

    /**
     * 将响应对象序列化为 JSON 字符串，避免测试依赖手写 JSON 格式。
     */
    private String json(Map<String, Object> body) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    /**
     * 从 MockRestServiceServer 捕获的请求体中读取 JSON-RPC 消息。
     */
    private JsonNode readRequest(MockClientHttpRequest request) throws IOException {
        return OBJECT_MAPPER.readTree(request.getBodyAsString());
    }

    /**
     * 记录每次 MCP HTTP 请求的方法名、协议版本头和会话头。
     *
     * @author myoung
     */
    private static final class RecordedRequests {
        private final List<String> methods = new CopyOnWriteArrayList<>();
        private final List<String> protocolHeaders = new CopyOnWriteArrayList<>();
        private final List<String> sessionHeaders = new CopyOnWriteArrayList<>();

        /**
         * 记录一次请求的关键观测字段。
         */
        private void add(String method, String protocolHeader, String sessionHeader) {
            methods.add(method);
            protocolHeaders.add(protocolHeader);
            sessionHeaders.add(sessionHeader);
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
    }
}

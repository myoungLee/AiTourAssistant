/*
 * @author myoung
 */
package com.aitour.client.mcp.external;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.client.mcp.TravelTool;
import com.aitour.common.exception.ApiException;
import com.aitour.config.mcp.McpProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 外部 MCP Server 工具适配器，负责完成初始化、工具发现和真实工具调用。
 *
 * @author myoung
 */
public class ExternalMcpToolAdapter implements TravelTool {
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String PROTOCOL_HEADER = "MCP-Protocol-Version";
    private static final String CLIENT_NAME = "AiTourAssistant";
    private static final String CLIENT_VERSION = "0.0.1";

    private final RestClient restClient;
    private final McpProperties properties;
    private final String name;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestId = new AtomicLong(1);
    private final Object lifecycleMonitor = new Object();

    private volatile boolean initialized;
    private volatile String negotiatedProtocolVersion = DEFAULT_PROTOCOL_VERSION;
    private volatile String sessionId;
    private volatile List<String> cachedToolNames = List.of();

    /**
     * 构造外部 MCP 工具适配器，生产代码和测试代码都走这一套握手与调用逻辑。
     */
    public ExternalMcpToolAdapter(RestClient restClient, McpProperties properties, String name) {
        this.restClient = restClient;
        this.properties = properties;
        this.name = name;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    /**
     * 返回当前适配器所代理的逻辑工具名。
     */
    public String name() {
        return name;
    }

    @Override
    /**
     * 调用真实外部 MCP Server，先校验远端工具可用，再执行 tools/call。
     */
    public ToolResult execute(ToolRequest request) {
        ensureExternalConfigured();
        List<String> availableToolNames = listAvailableToolNames();
        if (!availableToolNames.contains(name)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "外部 MCP Server 未提供工具: " + name);
        }

        JsonNode resultNode = sendRequest("tools/call", Map.of(
                "name", name,
                "arguments", request.arguments() == null ? Map.of() : request.arguments()
        ));
        ToolResult toolResult = toToolResult(resultNode);
        if (!toolResult.success()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_TOOL_EXECUTION_FAILED", "外部 MCP Server 工具调用失败: " + toolResult.summary());
        }
        return toolResult;
    }

    /**
     * 查询远端 MCP Server 当前实际暴露的工具列表，并做进程内缓存。
     */
    public List<String> listAvailableToolNames() {
        ensureExternalConfigured();
        ensureInitialized();
        List<String> names = cachedToolNames;
        if (!names.isEmpty()) {
            return names;
        }
        synchronized (lifecycleMonitor) {
            if (!cachedToolNames.isEmpty()) {
                return cachedToolNames;
            }
            List<String> discoveredNames = new ArrayList<>();
            String cursor = null;
            do {
                Map<String, Object> params = new LinkedHashMap<>();
                if (StringUtils.hasText(cursor)) {
                    params.put("cursor", cursor);
                }
                JsonNode resultNode = sendRequestInternal("tools/list", params);
                JsonNode toolsNode = resultNode.path("tools");
                if (!toolsNode.isArray()) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_INVALID_RESPONSE", "外部 MCP Server tools/list 响应缺少 tools 数组");
                }
                for (JsonNode toolNode : toolsNode) {
                    String toolName = toolNode.path("name").asText("");
                    if (StringUtils.hasText(toolName)) {
                        discoveredNames.add(toolName);
                    }
                }
                cursor = resultNode.path("nextCursor").asText("");
            } while (StringUtils.hasText(cursor));
            cachedToolNames = discoveredNames.stream().distinct().sorted().toList();
            return cachedToolNames;
        }
    }

    /**
     * 确保外部地址已经配置，避免 external 模式下静默回退为伪成功结果。
     */
    private void ensureExternalConfigured() {
        if (!properties.external().isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_EXTERNAL_NOT_CONFIGURED", "外部 MCP Server 未配置，请设置 mcp.external.base-url");
        }
    }

    /**
     * 按 MCP 生命周期完成 initialize 和 initialized，建立会话与协议版本。
     */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (lifecycleMonitor) {
            if (initialized) {
                return;
            }
            Map<String, Object> initializeParams = new LinkedHashMap<>();
            initializeParams.put("protocolVersion", DEFAULT_PROTOCOL_VERSION);
            initializeParams.put("capabilities", Map.of());
            initializeParams.put("clientInfo", Map.of(
                    "name", CLIENT_NAME,
                    "version", CLIENT_VERSION
            ));
            RawMcpResponse initializeResponse = postMessage(buildRequest("initialize", initializeParams), false);
            JsonNode initializeBody = readJsonRpcResponse(initializeResponse, "initialize", null);
            JsonNode initializeResult = initializeBody.path("result");
            if (initializeResult.isMissingNode() || initializeResult.isNull()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_INVALID_RESPONSE", "外部 MCP Server initialize 响应缺少 result");
            }
            String protocolVersion = initializeResult.path("protocolVersion").asText("");
            negotiatedProtocolVersion = StringUtils.hasText(protocolVersion) ? protocolVersion : DEFAULT_PROTOCOL_VERSION;
            sessionId = initializeResponse.headers().getFirst(SESSION_HEADER);
            sendInitializedNotification();
            initialized = true;
        }
    }

    /**
     * 发送 initialized 通知，告知服务端客户端已经进入正常操作阶段。
     */
    private void sendInitializedNotification() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", JSON_RPC_VERSION);
        payload.put("method", "notifications/initialized");
        RawMcpResponse response = postMessage(payload, true);
        if (!response.status().is2xxSuccessful()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_INITIALIZATION_FAILED", "外部 MCP Server 未接受 initialized 通知");
        }
    }

    /**
     * 发送 JSON-RPC 请求；如果遇到会话过期 404，则按协议重新初始化后重试一次。
     */
    private JsonNode sendRequest(String method, Map<String, Object> params) {
        ensureInitialized();
        try {
            return sendRequestInternal(method, params);
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND && "MCP_SESSION_EXPIRED".equals(ex.getCode())) {
                resetSession();
                ensureInitialized();
                return sendRequestInternal(method, params);
            }
            throw ex;
        }
    }

    /**
     * 发起一次真实 JSON-RPC 请求，并把应用级错误统一转换为可追踪业务异常。
     */
    private JsonNode sendRequestInternal(String method, Map<String, Object> params) {
        Map<String, Object> payload = buildRequest(method, params);
        Long expectedId = ((Number) payload.get("id")).longValue();
        RawMcpResponse response = postMessage(payload, true);
        JsonNode responseBody = readJsonRpcResponse(response, method, expectedId);
        JsonNode errorNode = responseBody.path("error");
        if (!errorNode.isMissingNode() && !errorNode.isNull()) {
            String errorMessage = errorNode.path("message").asText("外部 MCP Server 返回错误");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_PROTOCOL_ERROR", "外部 MCP Server 调用失败: " + errorMessage);
        }
        JsonNode resultNode = responseBody.path("result");
        if (resultNode.isMissingNode() || resultNode.isNull()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_INVALID_RESPONSE", "外部 MCP Server 响应缺少 result");
        }
        return resultNode;
    }

    /**
     * 构造标准 JSON-RPC 请求体，并为每次请求分配唯一 request id。
     */
    private Map<String, Object> buildRequest(String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", JSON_RPC_VERSION);
        payload.put("id", requestId.getAndIncrement());
        payload.put("method", method);
        if (params != null && !params.isEmpty()) {
            payload.put("params", params);
        }
        return payload;
    }

    /**
     * 以 Streamable HTTP 方式向远端发送 JSON-RPC 消息，同时兼容 JSON 与 SSE 响应。
     */
    private RawMcpResponse postMessage(Map<String, Object> payload, boolean includeProtocolHeader) {
        try {
            return restClient.method(HttpMethod.POST)
                    .uri(properties.external().baseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .headers(headers -> applyHeaders(headers, includeProtocolHeader))
                    .body(payload)
                    .exchange((request, response) -> toRawResponse(response));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_EXTERNAL_CALL_FAILED", "调用外部 MCP Server 失败: " + ex.getMessage());
        }
    }

    /**
     * 写入协议版本和会话头，确保后续请求满足 MCP HTTP 传输约定。
     */
    private void applyHeaders(HttpHeaders headers, boolean includeProtocolHeader) {
        if (includeProtocolHeader) {
            headers.set(PROTOCOL_HEADER, negotiatedProtocolVersion);
        }
        if (StringUtils.hasText(sessionId)) {
            headers.set(SESSION_HEADER, sessionId);
        }
    }

    /**
     * 将 HTTP 响应提炼为轻量对象，便于后续统一解析和错误处理。
     */
    private RawMcpResponse toRawResponse(ClientHttpResponse response) throws IOException {
        String body = response.getBody() == null ? "" : StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        return new RawMcpResponse(response.getStatusCode(), response.getHeaders(), body);
    }

    /**
     * 读取 JSON-RPC 响应；同时处理标准 JSON 响应和 SSE 文本流响应。
     */
    private JsonNode readJsonRpcResponse(RawMcpResponse response, String method, Long expectedId) {
        if (response.status().value() == HttpStatus.NOT_FOUND.value() && StringUtils.hasText(sessionId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MCP_SESSION_EXPIRED", "外部 MCP Server 会话已过期，需要重新初始化");
        }
        if (!response.status().is2xxSuccessful()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_EXTERNAL_CALL_FAILED", buildHttpErrorMessage(method, response));
        }
        if (!StringUtils.hasText(response.body())) {
            return objectMapper.createObjectNode();
        }
        try {
            MediaType contentType = response.headers().getContentType();
            if (contentType != null && MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
                return extractJsonRpcFromSse(response.body(), expectedId);
            }
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_INVALID_RESPONSE", "外部 MCP Server 返回了无法解析的 JSON 响应");
        }
    }

    /**
     * 解析 SSE 结果，提取最终对应本次请求的 JSON-RPC 响应消息。
     */
    private JsonNode extractJsonRpcFromSse(String body, Long expectedId) {
        List<JsonNode> events = new ArrayList<>();
        String[] blocks = body.split("\\r?\\n\\r?\\n");
        for (String block : blocks) {
            if (!StringUtils.hasText(block)) {
                continue;
            }
            StringBuilder dataBuilder = new StringBuilder();
            for (String line : block.split("\\r?\\n")) {
                if (line.startsWith("data:")) {
                    if (!dataBuilder.isEmpty()) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(line.substring(5).trim());
                }
            }
            if (!StringUtils.hasText(dataBuilder.toString())) {
                continue;
            }
            try {
                events.add(objectMapper.readTree(dataBuilder.toString()));
            } catch (JsonProcessingException ex) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_INVALID_RESPONSE", "外部 MCP Server SSE 响应中包含无效 JSON 数据");
            }
        }
        if (events.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_INVALID_RESPONSE", "外部 MCP Server SSE 响应中未找到 JSON-RPC 消息");
        }
        if (expectedId != null) {
            for (JsonNode event : events) {
                if (event.path("id").canConvertToLong() && Objects.equals(event.path("id").asLong(), expectedId)) {
                    return event;
                }
            }
        }
        return events.get(events.size() - 1);
    }

    /**
     * 将远端 CallToolResult 转换为项目内部统一 ToolResult。
     */
    private ToolResult toToolResult(JsonNode resultNode) {
        boolean success = !resultNode.path("isError").asBoolean(false);
        Map<String, Object> data = toStructuredContent(resultNode.path("structuredContent"));
        String summary = buildSummary(resultNode.path("content"), data);
        return new ToolResult(name, success, summary, data);
    }

    /**
     * 提取 structuredContent 作为结构化结果；没有时返回空对象。
     */
    private Map<String, Object> toStructuredContent(JsonNode structuredContentNode) {
        if (!structuredContentNode.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(structuredContentNode, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 优先从 text content 中生成摘要，没有文本时退化为结构化 JSON 字符串。
     */
    private String buildSummary(JsonNode contentNode, Map<String, Object> data) {
        List<String> textParts = new ArrayList<>();
        if (contentNode.isArray()) {
            for (JsonNode itemNode : contentNode) {
                if ("text".equals(itemNode.path("type").asText()) && StringUtils.hasText(itemNode.path("text").asText())) {
                    textParts.add(itemNode.path("text").asText());
                }
            }
        }
        if (!textParts.isEmpty()) {
            return String.join("\n", textParts);
        }
        if (!data.isEmpty()) {
            try {
                return objectMapper.writeValueAsString(data);
            } catch (JsonProcessingException ex) {
                return name + " 调用成功";
            }
        }
        return name + " 调用成功";
    }

    /**
     * 生成 HTTP 维度的错误信息，便于定位外部服务状态异常。
     */
    private String buildHttpErrorMessage(String method, RawMcpResponse response) {
        String detail = StringUtils.hasText(response.body()) ? response.body() : "无响应体";
        return "外部 MCP Server 调用失败，method=" + method + "，status=" + response.status().value() + "，body=" + detail;
    }

    /**
     * 清理当前会话缓存，为按协议重新初始化做准备。
     */
    private void resetSession() {
        synchronized (lifecycleMonitor) {
            initialized = false;
            sessionId = null;
            negotiatedProtocolVersion = DEFAULT_PROTOCOL_VERSION;
            cachedToolNames = List.of();
        }
    }

    /**
     * 保存一次 HTTP 交互结果，减少方法之间传递原始响应对象的耦合。
     *
     * @author myoung
     */
    private record RawMcpResponse(HttpStatusCode status, HttpHeaders headers, String body) {
    }
}

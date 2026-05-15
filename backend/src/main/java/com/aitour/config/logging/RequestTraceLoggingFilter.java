/*
 * @author myoung
 */
package com.aitour.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 为每个请求生成 traceId，并记录请求进入、完成和失败日志。
 *
 * @author myoung
 */
@Component
public class RequestTraceLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestTraceLoggingFilter.class);
    private static final String TRACE_ID = "traceId";
    private static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * 包装整个请求链路，记录请求摘要、耗时、状态码和异常。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        Instant start = Instant.now();
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        log.info("request_in traceId={} method={} uri={} hasQuery={} params={} remote={} userAgent={} contentType={} contentLength={} forwardedFor={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString() != null,
                summarizeParameters(request),
                request.getRemoteAddr(),
                abbreviate(request.getHeader("User-Agent")),
                Optional.ofNullable(request.getContentType()).orElse(""),
                request.getContentLengthLong(),
                abbreviate(resolveForwardedFor(request)));
        try {
            filterChain.doFilter(request, response);
            log.info("request_out traceId={} method={} uri={} status={} durationMs={} responseContentType={} responseTraceId={}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    Duration.between(start, Instant.now()).toMillis(),
                    Optional.ofNullable(response.getContentType()).orElse(""),
                    response.getHeader(TRACE_HEADER));
        } catch (Exception ex) {
            log.error("request_error traceId={} method={} uri={} status={} durationMs={} message={}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    Duration.between(start, Instant.now()).toMillis(),
                    ex.getMessage(),
                    ex);
            throw ex;
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    /**
     * 优先复用上游传入的 traceId，没有时本地生成 UUID。
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }

    /**
     * 提取反向代理透传的客户端地址，便于本地联调和后续部署排查链路来源。
     */
    private String resolveForwardedFor(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return "";
    }

    /**
     * 汇总请求参数并对敏感字段脱敏，便于排查入参问题且不泄露密码或令牌。
     */
    private String summarizeParameters(HttpServletRequest request) {
        Map<String, String> parameters = new TreeMap<>();
        request.getParameterMap().forEach((name, values) -> parameters.put(name, summarizeParameterValue(name, values)));
        return parameters.toString();
    }

    /**
     * 单个请求参数按字段名决定是否脱敏，并限制日志长度。
     */
    private String summarizeParameterValue(String name, String[] values) {
        if (isSensitiveField(name)) {
            return "[REDACTED]";
        }
        if (values == null || values.length == 0) {
            return "";
        }
        if (values.length == 1) {
            return abbreviate(values[0]);
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(abbreviate(values[index]));
        }
        builder.append(']');
        return builder.toString();
    }

    /**
     * 判断参数名是否包含密码、令牌或密钥等敏感信息标识。
     */
    private boolean isSensitiveField(String name) {
        String lowerName = name == null ? "" : name.toLowerCase();
        return lowerName.contains("password")
                || lowerName.contains("token")
                || lowerName.contains("secret")
                || lowerName.contains("authorization")
                || lowerName.contains("apiKey".toLowerCase())
                || lowerName.endsWith("key");
    }

    /**
     * 限制单个请求属性日志长度，避免超长请求头把控制台和文件日志刷满。
     */
    private String abbreviate(String value) {
        if (value == null || value.length() <= 200) {
            return value;
        }
        return value.substring(0, 200);
    }
}

/*
 * @author myoung
 */
package com.aitour.client.mcp.local;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.client.mcp.TravelTool;
import com.aitour.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 本地预算占位工具已禁用，避免运行时生成模拟预算数据。
 *
 * @author myoung
 */
@Component
public class LocalBudgetTool implements TravelTool {
    /**
     * 返回预算估算工具的标准名称。
     */
    @Override
    public String name() {
        return "budget.estimate";
    }

    /**
     * 本地模式不再生成模拟预算，调用时直接暴露配置问题。
     */
    @Override
    public ToolResult execute(ToolRequest request) {
        throw disabledException();
    }

    /**
     * 构造统一的本地工具禁用异常，提醒必须配置真实外部 MCP 服务。
     */
    private ApiException disabledException() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "LOCAL_MCP_TOOL_DISABLED",
                "本地预算占位工具已禁用，请配置 mcp.mode=external 和真实 mcp.external.base-url 后再调用 " + name()
        );
    }
}

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
 * 本地景点推荐占位工具已禁用，避免运行时生成虚假景点数据。
 *
 * @author myoung
 */
@Component
public class LocalPlaceSearchTool implements TravelTool {
    /**
     * 返回景点搜索工具的标准名称。
     */
    @Override
    public String name() {
        return "place.search";
    }

    /**
     * 本地模式不再生成模拟景点，调用时直接暴露配置问题。
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
                "本地景点占位工具已禁用，请配置 mcp.mode=external 和真实 mcp.external.base-url 后再调用 " + name()
        );
    }
}

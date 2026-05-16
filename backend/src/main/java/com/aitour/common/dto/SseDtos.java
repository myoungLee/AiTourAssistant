/*
 * @author myoung
 */
package com.aitour.common.dto;

import java.util.Map;

/**
 * SSE 流式接口统一事件载荷，保证前端可以按事件名稳定解析进度、工具结果和 AI 增量。
 *
 * @author myoung
 */
public final class SseDtos {
    /**
     * 隐藏工具类构造器，避免被实例化。
     */
    private SseDtos() {
    }

    /**
     * 表示当前规划步骤和整体进度百分比。
     */
    public record ProgressEvent(String step, String message, int percent) {
    }

    /**
     * 表示 AI 文本增量片段。
     */
    public record AiDeltaEvent(String text) {
    }

    /**
     * 表示 MCP 工具调用结果，data 保留结构化字段供前端渲染。
     */
    public record ToolResultEvent(String tool, String summary, Map<String, Object> data) {
    }

    /**
     * 表示某一天的行程快照，items 可承载后端规划出的临时条目列表。
     */
    public record PlanSnapshotEvent(Integer dayIndex, Object items) {
    }

    /**
     * 表示流式任务完成，并返回最终行程 id 和状态。
     */
    public record CompletedEvent(Long planId, String status) {
    }

    /**
     * 表示流式任务失败时的稳定错误码和可读提示。
     */
    public record ErrorEvent(String code, String message) {
    }
}

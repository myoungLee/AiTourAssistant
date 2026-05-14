/*
 * @author myoung
 */
package com.aitour.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * LLM 调用日志实体，用于后续排查 AI 请求和成本。
 *
 * @author myoung
 */
@TableName("llm_call_log")
public class LlmCallLog {
    private Long id;
    private Long userId;
    private Long planId;
    private String provider;
    private String model;
    private String promptSummary;
    private String responseSummary;
    private String tokenUsageJson;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPromptSummary() {
        return promptSummary;
    }

    public void setPromptSummary(String promptSummary) {
        this.promptSummary = promptSummary;
    }

    public String getResponseSummary() {
        return responseSummary;
    }

    public void setResponseSummary(String responseSummary) {
        this.responseSummary = responseSummary;
    }

    public String getTokenUsageJson() {
        return tokenUsageJson;
    }

    public void setTokenUsageJson(String tokenUsageJson) {
        this.tokenUsageJson = tokenUsageJson;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

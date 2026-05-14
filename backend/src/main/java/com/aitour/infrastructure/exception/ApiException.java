/*
 * @author myoung
 */
package com.aitour.infrastructure.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常统一携带 HTTP 状态码和稳定错误码。
 *
 * @author myoung
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

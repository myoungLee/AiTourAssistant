/*
 * @author myoung
 */
package com.aitour.common.exception;

import com.aitour.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 统一转换 API 异常，保证前端能稳定读取 code 和 msg。
 *
 * @author myoung
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 将业务异常转换为统一响应对象，同时保留异常携带的 HTTP 状态码。
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Result<Void>> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Result.error(ex.getMessage()));
    }

    /**
     * 将 Bean Validation 参数校验失败转换为统一失败响应。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error("请求参数不合法"));
    }

    /**
     * 将必填请求参数缺失转换为统一失败响应。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error("缺少请求参数：" + ex.getParameterName()));
    }

    /**
     * 将 RequestParam 等方法参数校验失败转换为统一失败响应。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error("请求参数不合法"));
    }

    /**
     * 将 Spring MVC 方法参数校验失败转换为统一失败响应。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error("请求参数不合法"));
    }

    /**
     * 将请求参数类型转换失败转换为统一失败响应，例如日期或数字格式错误。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error("请求参数格式错误：" + ex.getName()));
    }
}

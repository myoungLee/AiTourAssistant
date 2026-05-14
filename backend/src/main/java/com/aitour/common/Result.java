/*
 * @author myoung
 */
package com.aitour.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一接口响应对象，约定 code 为 1 表示成功，0 和其他数字表示失败。
 *
 * @author myoung
 */
@Schema(description = "统一接口响应对象")
public class Result<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "编码：1 成功，0 和其它数字为失败", example = "1")
    private Integer code;

    @Schema(description = "错误信息")
    private String msg;

    @Schema(description = "响应数据")
    private T data;

    /**
     * 创建无数据的成功响应。
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.code = 1;
        return result;
    }

    /**
     * 创建携带数据的成功响应。
     */
    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<>();
        result.data = object;
        result.code = 1;
        return result;
    }

    /**
     * 创建失败响应。
     */
    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.msg = msg;
        result.code = 0;
        return result;
    }

    /**
     * 返回业务编码。
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 设置业务编码，供 JSON 反序列化或框架绑定使用。
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * 返回错误信息。
     */
    public String getMsg() {
        return msg;
    }

    /**
     * 设置错误信息，供 JSON 反序列化或框架绑定使用。
     */
    public void setMsg(String msg) {
        this.msg = msg;
    }

    /**
     * 返回响应数据。
     */
    public T getData() {
        return data;
    }

    /**
     * 设置响应数据，供 JSON 反序列化或框架绑定使用。
     */
    public void setData(T data) {
        this.data = data;
    }
}

/*
 * @author myoung
 */
package com.aitour.infrastructure.security;

/**
 * 从 JWT 中解析出的当前登录用户。
 *
 * @author myoung
 */
public record CurrentUser(Long id, String username) {
}

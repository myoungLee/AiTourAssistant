/*
 * @author myoung
 */
package com.aitour.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 提供基础健康检查接口，用于本地联调和部署探活。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "aitour-backend",
                "timestamp", Instant.now().toString()
        );
    }
}

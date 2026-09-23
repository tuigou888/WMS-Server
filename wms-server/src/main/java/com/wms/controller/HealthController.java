package com.wms.controller;

import com.wms.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) { this.dataSource = dataSource; }

    /** 存活探针：进程能够处理 HTTP 请求即可返回。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "time", LocalDateTime.now(), "service", "wms-server"));
    }

    /** 就绪探针：只有数据库可用时才允许流量进入实例。 */
    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ready() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "READY", "database", "UP", "time", LocalDateTime.now())));
            }
        } catch (Exception ignored) {
            // 对外只返回依赖不可用，不暴露数据库连接细节。
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(503, "数据库暂不可用", Map.of("status", "NOT_READY", "database", "DOWN", "time", LocalDateTime.now())));
    }
}

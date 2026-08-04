package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.model.entity.OperationLog;
import com.wms.repository.OperationLogRepository;
import com.wms.security.Permissions;
import com.wms.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/logs")
public class OperationLogController {

    private final OperationLogRepository repo;

    public OperationLogController(OperationLogRepository repo) {
        this.repo = repo;
    }

    /** 查询日志列表（管理员）。日志写入仅由 OperationLogAspect 自动完成，不开放手动写入，避免审计数据被伪造。 */
    @GetMapping
    @PreAuthorize("hasAuthority('log:view')")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        ensureAdmin();
        List<Map<String, Object>> rows = repo.search(username, action, result,
                        parse(from), parse(to)).stream().map(this::view).toList();
        int max = Math.min(Math.max(pageSize, 1), 200);
        int start = page > 0 ? Math.min((page - 1) * max, rows.size()) : 0;
        int end = Math.min(start + max, rows.size());
        return ApiResponse.ok(new java.util.ArrayList<>(rows.subList(start, end)));
    }

    private Map<String, Object> view(OperationLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("username", l.getUsername());
        m.put("action", l.getAction());
        m.put("target", l.getTarget());
        m.put("method", l.getMethod());
        m.put("path", l.getPath());
        m.put("result", l.getResult());
        m.put("message", l.getMessage());
        m.put("operationAt", l.getOperationAt());
        return m;
    }

    private void ensureAdmin() {
        SecurityUtils.require(Permissions.LOG_VIEW);
    }

    private LocalDateTime parse(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDateTime.parse(s.replace(" ", "T"));
    }
}

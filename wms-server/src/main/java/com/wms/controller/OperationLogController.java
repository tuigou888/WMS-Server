package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.model.entity.OperationLog;
import com.wms.repository.OperationLogRepository;
import com.wms.security.Permissions;
import com.wms.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        ensureAdmin();
        int size = Math.min(Math.max(pageSize, 1), 200);
        int current = Math.max(page, 1);
        // 分页下推到 SQL：审计表只增不删，全表捞进内存会随运行时间线性变慢
        Page<OperationLog> p = repo.searchPage(username, action, result, parse(from), parse(to),
                PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "operationAt")));
        return ApiResponse.ok(Map.of("records", p.getContent().stream().map(this::view).toList(),
                "total", p.getTotalElements(), "page", current, "pageSize", size));
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

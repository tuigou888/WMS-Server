package com.wms.service;

import com.wms.model.entity.OperationLog;
import com.wms.repository.OperationLogRepository;
import com.wms.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 操作日志服务：以嵌套事务写入，避免被业务回滚连带丢日志。
 */
@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);
    private final OperationLogRepository repo;

    public OperationLogService(OperationLogRepository repo) { this.repo = repo; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, String action, String target,
                       String method, String path, String body, String result, String message) {
        try {
            OperationLog l = new OperationLog();
            l.setUsername(username == null ? "anonymous" : username);
            l.setAction(action == null ? guessAction(method, path) : action);
            l.setTarget(target);
            l.setMethod(method);
            l.setPath(path);
            l.setRequestBody(truncate(body, 1000));
            l.setResult(result);
            l.setMessage(truncate(message, 500));
            l.setOperationAt(LocalDateTime.now());
            repo.save(l);
        } catch (Exception e) {
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    public void record(TokenService.Principal principal, String action, String target,
                       String method, String path, String body, String result, String message) {
        record(principal == null ? null : principal.username(), action, target, method, path, body, result, message);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 根据请求方法与路径推断中文动作名，作为兜底（AOP 一般会显式传 action）。 */
    public static String guessAction(String method, String path) {
        if (path == null) return "访问";
        if (path.startsWith("/auth/login")) return "登录";
        if (path.startsWith("/auth/logout")) return "退出登录";
        if (path.startsWith("/auth/users")) return "用户管理";
        if (path.startsWith("/documents/")) return path.contains("/complete") ? "执行单据"
                : path.contains("/review") ? "审核单据"
                : path.contains("/cancel") ? "取消单据"
                : path.contains("/void") ? "反审单据"
                : path.contains("/reverse") ? "红冲单据"
                : "出入库单";
        if (path.startsWith("/transfers/")) return path.contains("/complete") ? "执行调拨"
                : path.contains("/review") ? "审核调拨" : "调拨单";
        if (path.startsWith("/stocktakes/")) return path.contains("/complete") ? "完成盘点"
                : path.contains("/review") ? "审核盘点"
                : path.contains("/count") ? "录入实盘" : "盘点单";
        if (path.startsWith("/adjustments/")) return path.contains("/complete") ? "执行报损报溢"
                : path.contains("/review") ? "审核报损报溢" : "报损报溢单";
        if (path.startsWith("/inventory") || path.startsWith("/stock/")) return "库存操作";
        if (path.startsWith("/items")) return "物品管理";
        if (path.startsWith("/partners")) return "往来单位管理";
        if (path.startsWith("/warehouses")) return "仓库管理";
        return path;
    }
}
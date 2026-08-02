package com.wms.aspect;

import com.wms.common.ApiResponse;
import com.wms.security.TokenService;
import com.wms.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Aspect
@Component
public class OperationLogAspect {

    private final OperationLogService logs;
    private final TokenService tokens;

    public OperationLogAspect(OperationLogService logs, TokenService tokens) {
        this.logs = logs;
        this.tokens = tokens;
    }

    @Around("execution(* com.wms.controller..*(..)) && !within(com.wms.controller.OperationLogController)")
    public Object logController(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest req = attrs != null ? attrs.getRequest() : null;
        String path = req != null ? req.getRequestURI() : "";
        String method = req != null ? req.getMethod() : "";
        String auth = req != null ? req.getHeader("Authorization") : null;
        TokenService.Principal principal = resolve(auth);
        String username = principal == null ? "anonymous" : principal.username();

        long start = System.currentTimeMillis();
        Object result;
        String resultStatus = "ERROR";
        String message = "unknown";
        try {
            result = pjp.proceed();
            resultStatus = "SUCCESS";
            message = "ok";
        } catch (Throwable t) {
            message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            throw t;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String action = OperationLogService.guessAction(method, path);
            String body = extractBody(req);
            logs.record(username, action, null, method, path, body, resultStatus, message + " (" + elapsed + "ms)");
        }
        return result;
    }

    private TokenService.Principal resolve(String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            Optional<TokenService.Principal> p = tokens.resolve(auth.substring(7));
            return p.orElse(null);
        }
        return null;
    }

    private String extractBody(HttpServletRequest req) {
        if (req == null) return null;
        String ct = req.getContentType() != null ? req.getContentType() : "";
        if (ct.contains("multipart")) return "<multipart>";
        return null; // body not captured for non-multipart (would need ContentCachingRequestWrapper)
    }
}
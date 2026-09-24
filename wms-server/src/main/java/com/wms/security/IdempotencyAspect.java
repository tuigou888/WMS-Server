package com.wms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.wms.common.BusinessException;
import com.wms.model.entity.IdempotentRequest;
import com.wms.repository.IdempotentRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdempotencyAspect {
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private final IdempotentRequestRepository requests;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public IdempotencyAspect(IdempotentRequestRepository requests, ObjectMapper mapper,
                             PlatformTransactionManager transactionManager) {
        this.requests = requests; this.mapper = mapper; this.transactions = new TransactionTemplate(transactionManager);
    }

    // 不用 @annotation(idempotent) 参数绑定：运行时 JoinPointMatch 绑定失败会导致所有幂等接口 500，改为在通知内反射获取注解
    @Around("@annotation(com.wms.security.Idempotent)")
    public Object executeOnce(ProceedingJoinPoint joinPoint) throws Throwable {
        Idempotent idempotent = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Idempotent.class);
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        String key = request == null ? null : request.getHeader("Idempotency-Key");
        if (key == null || !KEY_PATTERN.matcher(key).matches()) throw new BusinessException("缺少有效的 Idempotency-Key");
        String username = SecurityUtils.username();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String scope = idempotent.value().isBlank() ? signature.getDeclaringTypeName() + "#" + signature.getName() : idempotent.value();
        String requestHash = hash(mapper.writeValueAsString(joinPoint.getArgs()));
        JavaType returnType = mapper.getTypeFactory().constructType(signature.getMethod().getGenericReturnType());
        return transactions.execute(status -> {
            requests.insertIfAbsent(username, scope, key, requestHash);
            IdempotentRequest record = requests.findForUpdate(username, scope, key)
                    .orElseThrow(() -> new IllegalStateException("幂等请求记录创建失败"));
            if (!record.getRequestHash().equals(requestHash)) throw new BusinessException("Idempotency-Key 已用于不同请求");
            if (record.getResponseJson() != null) {
                try { return mapper.readValue(record.getResponseJson(), returnType); }
                catch (Exception e) { throw new IllegalStateException("幂等响应无法读取", e); }
            }
            try {
                Object response = joinPoint.proceed();
                record.setResponseJson(mapper.writeValueAsString(response));
                requests.save(record);
                return response;
            } catch (Throwable e) {
                if (e instanceof RuntimeException runtime) throw runtime;
                if (e instanceof Error error) throw error;
                throw new IllegalStateException(e);
            }
        });
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 不可用", e); }
    }
}

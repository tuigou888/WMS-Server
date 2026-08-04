package com.wms.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException e) { return ResponseEntity.status(403).body(ApiResponse.error(403, e.getMessage())); }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ApiResponse<Void>> rateLimited(RateLimitedException e) {
        return ResponseEntity.status(429).body(ApiResponse.error(429, e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
    }

    /** 唯一约束/外键等数据库完整性冲突：如并发绑定同一 openid、重复编码创建。 */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> dataIntegrity(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("数据完整性冲突", e);
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "数据冲突，请刷新后重试"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> uploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "上传文件过大"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(x -> x.getField() + " " + x.getDefaultMessage()).orElse("参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> violation(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            java.time.format.DateTimeParseException.class,
            java.lang.NumberFormatException.class, java.lang.ArithmeticException.class})
    public ResponseEntity<ApiResponse<Void>> badInput(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "参数格式错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.internalServerError().body(ApiResponse.error(500, "服务处理失败"));
    }
}

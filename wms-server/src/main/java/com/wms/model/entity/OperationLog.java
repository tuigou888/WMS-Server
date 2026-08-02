package com.wms.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 操作日志（审计追踪）。AOP 切面统一写入。
 */
@Entity
@Table(name = "operation_logs")
public class OperationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=50) private String username;
    @Column(nullable=false, length=50) private String action;       // 例如：登录 / 创建入库单 / 执行出库单
    @Column(length=200) private String target;                      // 例如：单据号
    @Column(length=20) private String method;                       // HTTP 方法
    @Column(length=200) private String path;                        // HTTP 路径
    private String requestBody;                                     // 请求体（截断）
    private String result;                                          // SUCCESS / ERROR
    @Column(length=500) private String message;                     // 结果描述 / 错误信息
    @Column(nullable=false) private LocalDateTime operationAt = LocalDateTime.now();

    public Long getId(){return id;}
    public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getAction(){return action;} public void setAction(String v){action=v;}
    public String getTarget(){return target;} public void setTarget(String v){target=v;}
    public String getMethod(){return method;} public void setMethod(String v){method=v;}
    public String getPath(){return path;} public void setPath(String v){path=v;}
    public String getRequestBody(){return requestBody;} public void setRequestBody(String v){requestBody=v;}
    public String getResult(){return result;} public void setResult(String v){result=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public LocalDateTime getOperationAt(){return operationAt;} public void setOperationAt(LocalDateTime v){operationAt=v;}
}
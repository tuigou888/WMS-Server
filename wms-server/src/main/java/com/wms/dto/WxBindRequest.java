package com.wms.dto;
import jakarta.validation.constraints.NotBlank;
/** bindTicket 只能由 /auth/wx-login 在短时效内签发，禁止客户端直接指定 openid。 */
public record WxBindRequest(@NotBlank(message="绑定凭据不能为空") String bindTicket, @NotBlank(message="用户名不能为空") String username, @NotBlank(message="密码不能为空") String password) {}

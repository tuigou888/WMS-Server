package com.wms.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
/** 微信首次登录的客户开户请求；角色由服务端固定为 CUSTOMER。 */
public record WxRegistrationRequest(@NotBlank(message="绑定凭据不能为空") String bindTicket,@NotBlank(message="用户名不能为空") @Size(max=50) String username,@NotBlank(message="密码不能为空") @Size(min=6,max=100) String password,@Size(max=255) String displayName) {}

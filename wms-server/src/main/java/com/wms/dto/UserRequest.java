package com.wms.dto;
import jakarta.validation.constraints.*;
public record UserRequest(@NotBlank String username,String password,@NotBlank String displayName,@NotBlank @Pattern(regexp="ADMIN|WAREHOUSE|PROCUREMENT|AUDITOR|FINANCE|CUSTOMER_SERVICE|CUSTOMER",message="角色不受支持") String role,Boolean enabled) {}

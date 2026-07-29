package com.wms.dto;
import jakarta.validation.constraints.*;
public record UserRequest(@NotBlank String username,String password,@NotBlank String displayName,@NotBlank @Pattern(regexp="ADMIN|WAREHOUSE",message="角色必须为 ADMIN 或 WAREHOUSE") String role,Boolean enabled) {}

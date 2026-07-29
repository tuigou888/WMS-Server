package com.wms.dto;
import jakarta.validation.constraints.*;
public record PartnerRequest(@NotBlank(message="编码不能为空") String code,@NotBlank(message="名称不能为空") String name,@NotBlank(message="类型不能为空") @Pattern(regexp="SUPPLIER|CUSTOMER|BOTH",message="类型必须为 SUPPLIER、CUSTOMER 或 BOTH") String type,String contactName,String phone,String email,String address,Boolean enabled,String remark) {}

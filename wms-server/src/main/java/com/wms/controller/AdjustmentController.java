package com.wms.controller;
import com.wms.common.ApiResponse; import com.wms.dto.*; import com.wms.service.AdjustmentService; import jakarta.validation.Valid; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/adjustments") public class AdjustmentController {
 private final AdjustmentService service; public AdjustmentController(AdjustmentService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('adjustment:read')") public ApiResponse<Map<String,Object>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return ApiResponse.ok(service.list(page,pageSize));}
 @com.wms.security.Idempotent @PostMapping @PreAuthorize("hasAuthority('adjustment:write')") public ApiResponse<Map<String,Object>> create(@Valid @RequestBody AdjustmentRequest r){return ApiResponse.ok("报损报溢草稿创建成功",service.create(r));}
 @com.wms.security.Idempotent @PostMapping("/{id}/review") @PreAuthorize("hasAuthority('adjustment:review')") public ApiResponse<Map<String,Object>> review(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return ApiResponse.ok("审核完成",service.review(id,r));}
 @com.wms.security.Idempotent @PostMapping("/{id}/complete") @PreAuthorize("hasAuthority('adjustment:execute')") public ApiResponse<Map<String,Object>> complete(@PathVariable Long id){return ApiResponse.ok("报损报溢已执行",service.complete(id));}
}

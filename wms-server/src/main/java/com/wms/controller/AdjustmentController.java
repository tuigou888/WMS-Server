package com.wms.controller;
import com.wms.common.ApiResponse; import com.wms.dto.*; import com.wms.service.AdjustmentService; import jakarta.validation.Valid; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/adjustments") public class AdjustmentController {
 private final AdjustmentService service; public AdjustmentController(AdjustmentService service){this.service=service;}
 @GetMapping public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(service.list());}
 @PostMapping public ApiResponse<Map<String,Object>> create(@Valid @RequestBody AdjustmentRequest r){return ApiResponse.ok("报损报溢草稿创建成功",service.create(r));}
 @PostMapping("/{id}/review") public ApiResponse<Map<String,Object>> review(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return ApiResponse.ok("审核完成",service.review(id,r));}
 @PostMapping("/{id}/complete") public ApiResponse<Map<String,Object>> complete(@PathVariable Long id){return ApiResponse.ok("报损报溢已执行",service.complete(id));}
}
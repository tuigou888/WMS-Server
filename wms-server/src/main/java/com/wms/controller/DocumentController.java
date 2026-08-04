package com.wms.controller;
import com.wms.common.ApiResponse; import com.wms.dto.*; import com.wms.service.DocumentService; import jakarta.validation.Valid; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping public class DocumentController {
 private final DocumentService service; public DocumentController(DocumentService service){this.service=service;}
 @GetMapping("/documents") public ApiResponse<List<Map<String,Object>>> documents(){return ApiResponse.ok(service.documents());}
 @GetMapping("/documents/{id}") public ApiResponse<Map<String,Object>> document(@PathVariable Long id){return ApiResponse.ok(service.documentDetail(id));}
 @PostMapping("/documents") public ApiResponse<Map<String,Object>> createDocument(@Valid @RequestBody DocumentRequest r){return ApiResponse.ok("草稿单创建成功",service.createDocument(r));}
  @PostMapping("/documents/{id}/review") @PreAuthorize("hasAuthority('document:review')") public ApiResponse<Map<String,Object>> reviewDocument(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return ApiResponse.ok("审核完成",service.reviewDocument(id,r));}
  @PostMapping("/documents/{id}/complete") public ApiResponse<Map<String,Object>> completeDocument(@PathVariable Long id){return ApiResponse.ok("单据执行完成",service.completeDocument(id));}
  @PostMapping("/documents/{id}/cancel") @PreAuthorize("hasAuthority('document:review')") public ApiResponse<Map<String,Object>> cancelDocument(@PathVariable Long id){return ApiResponse.ok("单据已取消",service.cancelDocument(id));}
  @PostMapping("/documents/{id}/uncomplete") @PreAuthorize("hasAuthority('document:review')") public ApiResponse<Map<String,Object>> uncompleteDocument(@PathVariable Long id){return ApiResponse.ok("单据已反审",service.uncompleteDocument(id));}
  @PostMapping("/documents/{id}/reverse") @PreAuthorize("hasAuthority('document:review')") public ApiResponse<Map<String,Object>> reverseDocument(@PathVariable Long id){return ApiResponse.ok("红冲单据已生成",service.reverseDocument(id));}
  @GetMapping("/transfers") public ApiResponse<List<Map<String,Object>>> transfers(){return ApiResponse.ok(service.transferList());}
  @PostMapping("/transfers") public ApiResponse<Map<String,Object>> createTransfer(@Valid @RequestBody TransferRequest r){return ApiResponse.ok("调拨草稿创建成功",service.createTransfer(r));}
  @PostMapping("/transfers/{id}/review") @PreAuthorize("hasAuthority('transfer:review')") public ApiResponse<Map<String,Object>> reviewTransfer(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return ApiResponse.ok("审核完成",service.reviewTransfer(id,r));}
  @PostMapping("/transfers/{id}/complete") public ApiResponse<Map<String,Object>> completeTransfer(@PathVariable Long id){return ApiResponse.ok("调拨已执行",service.completeTransfer(id));}
  @GetMapping("/stocktakes") public ApiResponse<List<Map<String,Object>>> stocktakes(){return ApiResponse.ok(service.stocktakeList());}
  @PostMapping("/stocktakes") public ApiResponse<Map<String,Object>> createStocktake(@Valid @RequestBody StocktakeRequest r){return ApiResponse.ok("盘点草稿创建成功",service.createStocktake(r));}
  @PostMapping("/stocktakes/{id}/count") public ApiResponse<Map<String,Object>> countStocktake(@PathVariable Long id,@Valid @RequestBody StocktakeRequest r){return ApiResponse.ok("实盘数量已保存",service.countStocktake(id,r));}
  @PostMapping("/stocktakes/{id}/review") @PreAuthorize("hasAuthority('stocktake:review')") public ApiResponse<Map<String,Object>> reviewStocktake(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return ApiResponse.ok("审核完成",service.reviewStocktake(id,r));}
  @PostMapping("/stocktakes/{id}/complete") public ApiResponse<Map<String,Object>> completeStocktake(@PathVariable Long id){return ApiResponse.ok("盘点调整已执行",service.completeStocktake(id));}
}

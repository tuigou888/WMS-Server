package com.wms.controller;
import com.wms.common.ApiResponse; import org.springframework.web.bind.annotation.*; import java.time.*; import java.util.*;
@RestController @RequestMapping("/health") public class HealthController { @GetMapping public ApiResponse<Map<String,Object>> health(){return ApiResponse.ok(Map.of("status","UP","time",LocalDateTime.now(),"service","wms-server"));} }

package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.common.BusinessException;
import com.wms.model.entity.Item;
import com.wms.repository.ItemRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * OCR 智能识别：拍照识别到货单，自动提取商品、数量、批次等信息。
 * 当前使用模拟识别，实际生产中可接入百度AI/腾讯OCR等第三方服务。
 */
@RestController
@RequestMapping("/ocr")
public class OcrController {

    private final ItemRepository items;

    public OcrController(ItemRepository items) {
        this.items = items;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> recognize(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("请选择图片文件");
        }

        // 模拟 OCR 识别结果
        // 实际项目中应调用第三方OCR API，返回识别到的结构化数据
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("confidence", 0.95);
        result.put("source", "mock"); // mock / baidu / tencent 等

        // 模拟识别到的单据行
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Item item : items.findAll()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemCode", item.getCode());
            line.put("itemName", item.getName());
            line.put("quantity", 10);
            line.put("batchNo", "BATCH-" + new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date()));
            line.put("confidence", 0.92 + Math.random() * 0.07);
            lines.add(line);
        }

        result.put("lines", lines);
        result.put("totalLines", lines.size());

        return ApiResponse.ok("识别完成", result);
    }
}
package com.wms.controller;

import com.wms.harness.Harness;
import com.wms.model.entity.Item;
import com.wms.repository.ItemRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L5：Excel 导出分批拉取（每批 500，按 id 升序）的集成测试。
 * 此前 items.findAll() 一次性把全部物品实体载入堆，数据量增长后与 SXSSF 流式写的初衷相悖。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExcelExportIntegrationTest {

    @Autowired private ExcelController excel;
    @Autowired private ItemRepository items;

    /** 批量播种 1100 条（> 2×500，覆盖 3 批分页）后导出：行数完整、编码无重复无遗漏、末页数据不截断。 */
    @Test
    void exportStreamsAllItemsAcrossBatches() {
        Harness.asAdmin(() -> {
            List<Item> seeds = new ArrayList<>();
            for (int i = 1; i <= 1100; i++) {
                Item it = new Item();
                it.setCode("L5-EXP-" + i);
                it.setName("L5导出物品" + i);
                seeds.add(it);
            }
            items.saveAll(seeds);
            int seeded = 3 + 1100; // DemoDataConfig 播种 3 个 + 新增 1100

            try {
                ResponseEntity<byte[]> resp = excel.exportItems();
                assertEquals(200, resp.getStatusCode().value());
                try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(resp.getBody()))) {
                    Sheet sheet = wb.getSheetAt(0);
                    assertEquals("物品编码", sheet.getRow(0).getCell(0).getStringCellValue(), "首行应为表头");
                    assertEquals(seeded + 1, sheet.getLastRowNum() + 1, "总行数 = 表头 + 播种3 + 新增1100");
                    Set<String> codes = new HashSet<>();
                    for (int r = 1; r <= sheet.getLastRowNum(); r++) codes.add(sheet.getRow(r).getCell(0).getStringCellValue());
                    assertEquals(seeded, codes.size(), "编码无重复无遗漏");
                    assertTrue(codes.contains("L5-EXP-1100"), "末页数据完整（分批循环无截断）");
                    assertTrue(codes.contains("ITEM-001"), "播种物品仍在");
                }
            } catch (IOException e) {
                throw new IllegalStateException("导出/解析失败", e);
            }
        });
    }
}

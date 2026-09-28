package com.wms.controller;

import com.wms.common.BusinessException;
import com.wms.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OcrControllerTest {
    @Test
    void productionModeRejectsMockOcr() {
        OcrController controller = new OcrController(mock(ItemRepository.class));
        ReflectionTestUtils.setField(controller, "mock", false);

        assertThrows(BusinessException.class, () -> controller.recognize(new MockMultipartFile("file", "arrival.png", "image/png", new byte[]{1})));
    }

    @Test
    void developmentMockModeReturnsExplicitMockSource() {
        OcrController controller = new OcrController(mock(ItemRepository.class));
        ReflectionTestUtils.setField(controller, "mock", true);

        var response = controller.recognize(new MockMultipartFile("file", "arrival.png", "image/png", new byte[]{1}));

        assertEquals("mock", response.data().get("source"));
    }

    @Test
    void rejectsNonImageContentType() {
        OcrController controller = new OcrController(mock(ItemRepository.class));
        ReflectionTestUtils.setField(controller, "mock", true);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.recognize(new MockMultipartFile(
                "file", "arrival.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1})));
        assertTrue(ex.getMessage().contains("仅支持图片"), "实际消息: " + ex.getMessage());
    }

    @Test
    void rejectsOversizedImage() {
        OcrController controller = new OcrController(mock(ItemRepository.class));
        ReflectionTestUtils.setField(controller, "mock", true);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.recognize(new MockMultipartFile(
                "file", "arrival.png", "image/png", new byte[8 * 1024 * 1024 + 1])));
        assertTrue(ex.getMessage().contains("压缩"), "实际消息: " + ex.getMessage());
    }
}

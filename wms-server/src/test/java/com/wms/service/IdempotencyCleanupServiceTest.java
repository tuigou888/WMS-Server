package com.wms.service;

import com.wms.model.entity.IdempotentRequest;
import com.wms.repository.IdempotentRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等请求记录清理：只删超过保留期的行，保留期内（含刚写入）的必须留下。
 * createdAt 由 @PrePersist 写入且无 setter，用原生 SQL 把旧行拨回 72h 前（默认保留期 48h）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdempotencyCleanupServiceTest {

    @Autowired private IdempotencyCleanupService cleanup;
    @Autowired private IdempotentRequestRepository requests;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void deletesOnlyExpiredRecords() {
        requests.saveAndFlush(new IdempotentRequest("admin", "scope#cleanup", "cleanup-old-key", "hash1"));
        requests.saveAndFlush(new IdempotentRequest("admin", "scope#cleanup", "cleanup-fresh-key", "hash2"));
        jdbc.update("update idempotent_requests set created_at = ? where request_key = ?",
                LocalDateTime.now().minusHours(72), "cleanup-old-key");

        int deleted = cleanup.runOnce();

        assertEquals(1, deleted);
        List<String> keys = requests.findAll().stream().map(IdempotentRequest::getRequestKey).toList();
        assertFalse(keys.contains("cleanup-old-key"), "超保留期的记录应被删除");
        assertTrue(keys.contains("cleanup-fresh-key"), "保留期内的记录必须保留");
    }

    @Test
    void noExpiredRecordsDeletesNothing() {
        requests.saveAndFlush(new IdempotentRequest("admin", "scope#cleanup", "cleanup-none-key", "hash3"));

        assertEquals(0, cleanup.runOnce());
        assertTrue(requests.findAll().stream().anyMatch(r -> "cleanup-none-key".equals(r.getRequestKey())));
    }
}

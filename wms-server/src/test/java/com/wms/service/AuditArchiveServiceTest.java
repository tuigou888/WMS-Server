package com.wms.service;

import com.wms.model.entity.OperationLog;
import com.wms.repository.OperationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A3②：审计日志归档。核心断言是"默认只归档不删"与"删除只作用于本轮刚归档的行"。
 * 用例先跑一轮只归档不清删除的 sweep，把其它用例可能遗留的超期日志推进水位，保证行数断言确定。
 * 被测对象直接 new（不用 Spring 单例），避免把 @TempDir 和删除开关写进共享上下文。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditArchiveServiceTest {

    private static final String WATERMARK = ".operation_logs.watermark";

    @TempDir Path archiveDir;
    @Autowired OperationLogRepository logs;
    @Autowired PlatformTransactionManager txManager;

    private AuditArchiveService service(boolean deleteEnabled) {
        AuditArchiveService s = new AuditArchiveService(logs, txManager);
        ReflectionTestUtils.setField(s, "afterDays", 30);
        ReflectionTestUtils.setField(s, "dir", archiveDir.toString());
        ReflectionTestUtils.setField(s, "batchSize", 2); // 故意小于用例数据量，覆盖多批游标推进
        ReflectionTestUtils.setField(s, "deleteEnabled", deleteEnabled);
        return s;
    }

    private OperationLog seed(String username, LocalDateTime at) {
        OperationLog l = new OperationLog();
        l.setUsername(username); l.setAction("登录"); l.setTarget("DOC-0001");
        l.setMethod("POST"); l.setPath("/auth/login"); l.setResult("SUCCESS");
        l.setMessage("ok"); l.setRequestBody("{}"); l.setOperationAt(at);
        return logs.save(l);
    }

    /** 清场：把历史遗留的超期日志归档掉、水位推到最大，但不删任何行。 */
    private void sweep() { service(false).runOnce(); }

    @Test
    @Transactional
    void archivesExpiredRowsWithoutDeleting() throws IOException {
        sweep();
        long base = logs.count();
        LocalDateTime now = LocalDateTime.now();
        seed("exp-1", now.minusDays(40));
        seed("exp-2", now.minusDays(35));
        seed("exp-3", now.minusDays(31));
        seed("fresh-1", now.minusDays(2));

        AuditArchiveService.Result r = service(false).runOnce();

        assertEquals(3, r.archived());
        assertEquals(0, r.deleted());
        assertNotNull(r.file());
        assertEquals(base + 4, logs.count(), "默认配置下不得删除任何日志");

        List<String> lines = Files.readAllLines(r.file());
        assertEquals(4, lines.size(), "表头 + 3 行超期数据");
        assertTrue(lines.get(0).startsWith("id,operation_at,username,"));
        String body = String.join("\n", lines);
        assertTrue(body.contains("exp-1") && body.contains("exp-2") && body.contains("exp-3"), body);
        assertFalse(body.contains("fresh-1"), "保留期内的日志不得进归档");
    }

    @Test
    @Transactional
    void watermarkMakesSecondRunEmpty() throws IOException {
        sweep();
        LocalDateTime now = LocalDateTime.now();
        seed("exp-1", now.minusDays(40));
        seed("exp-2", now.minusDays(40));
        service(false).runOnce();
        long afterFirst = logs.count();

        AuditArchiveService.Result second = service(true).runOnce();

        assertEquals(0, second.archived());
        assertEquals(0, second.deleted());
        assertNull(second.file(), "没有新数据时不应创建归档文件");
        assertEquals(afterFirst, logs.count(), "已归档过的行不得被后续轮次删除");
        assertTrue(Files.exists(Path.of(archiveDir.toString(), WATERMARK)));
    }

    @Test
    @Transactional
    void deleteOnlyRemovesArchivedRows() {
        sweep();
        long base = logs.count();
        LocalDateTime now = LocalDateTime.now();
        seed("exp-1", now.minusDays(400));
        seed("exp-2", now.minusDays(400));
        seed("exp-3", now.minusDays(400));
        seed("fresh-1", now.minusDays(1));
        seed("fresh-2", now.minusDays(1));

        AuditArchiveService.Result r = service(true).runOnce();

        assertEquals(3, r.archived());
        assertEquals(3, r.deleted());
        assertEquals(base + 2, logs.count());
    }

    @Test
    @Transactional
    void csvNeutralizesFormulaAndQuoteInjection() throws IOException {
        sweep();
        OperationLog l = seed("exp-1", LocalDateTime.now().minusDays(40));
        l.setTarget("=1+1|calc");
        l.setPath("@SUM(1)");
        l.setMessage("a\"b\nc");
        logs.save(l);

        AuditArchiveService.Result r = service(false).runOnce();

        assertEquals(1, r.archived());
        List<String> lines = Files.readAllLines(r.file());
        assertEquals(2, lines.size(), "字段内换行必须压成空格，不能撑出第三行");
        String row = lines.get(1);
        assertTrue(row.contains("\"'=1+1|calc\""), row);
        assertTrue(row.contains("\"'@SUM(1)\""), row);
        assertTrue(row.contains("\"a\"\"b c\""), row);
    }
}

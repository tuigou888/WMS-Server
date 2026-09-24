package com.wms.service;

import com.wms.model.entity.OperationLog;
import com.wms.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审计日志归档（上线整改 A3②）：把超过保留期的 operation_logs 每日导出成 CSV 归档文件。
 *
 * <p>默认只归档、不删数据；删除由 {@code audit.archive.delete-enabled} 显式打开，且只删除
 * 本次已同步落盘的归档行，因此任何一次删除都有对应 CSV 可回溯。
 *
 * <p>水位（已归档最大 id）写在归档目录的 {@code .operation_logs.watermark}。进程若被杀在
 * "CSV 已落盘、水位未推进"之间，下一轮会重复导出同一批：冗余但不丢数据。
 *
 * <p>多实例部署时本任务只应在一个实例上开启，直到 A7（ShedLock）落地。
 */
@Service
public class AuditArchiveService {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String HEADER = "id,operation_at,username,action,target,method,path,result,message,request_body";
    private static final String WATERMARK_FILE = ".operation_logs.watermark";

    @Value("${audit.archive.enabled:true}") private boolean enabled;
    @Value("${audit.archive.after-days:365}") private int afterDays;
    @Value("${audit.archive.dir:logs/archive}") private String dir;
    @Value("${audit.archive.batch-size:500}") private int batchSize;
    @Value("${audit.archive.delete-enabled:false}") private boolean deleteEnabled;

    private final OperationLogRepository logs;
    private final TransactionTemplate tx;

    public AuditArchiveService(OperationLogRepository logs, PlatformTransactionManager txManager) {
        this.logs = logs;
        this.tx = new TransactionTemplate(txManager);
    }

    /** archived=导出行数，deleted=从库中删除的行数，file=null 表示本轮无可归档数据。 */
    public record Result(int archived, int deleted, long watermark, Path file) {}

    /** 定时入口。手动执行（含测试）请直接调 {@link #runOnce()}，enabled 只控制是否自动跑。 */
    @Scheduled(cron = "${audit.archive.cron:0 30 3 * * *}")
    public void scheduledRun() {
        if (!enabled) return;
        try {
            Result r = runOnce();
            log.info("审计日志归档完成: archived={}, deleted={}, watermark={}, file={}", r.archived(), r.deleted(), r.watermark(), r.file());
        } catch (Exception e) {
            log.error("审计日志归档失败，水位之后的数据留给下一轮重试", e);
        }
    }

    /** 跑一轮归档。每批各自开事务（有外层事务时并入），删除也只按批提交。 */
    public Result runOnce() {
        LocalDateTime before = LocalDateTime.now().minusDays(afterDays);
        long cursor = readWatermark();
        int archived = 0;
        int deleted = 0;
        Path file = null;
        while (true) {
            final long afterId = cursor;
            List<OperationLog> batch = tx.execute(st -> logs.findArchiveBatch(afterId, before, PageRequest.of(0, batchSize)));
            if (batch == null || batch.isEmpty()) break;
            if (file == null) file = openFile();
            append(file, batch, archived == 0);
            cursor = batch.get(batch.size() - 1).getId();
            archived += batch.size();
            if (deleteEnabled) { deleteBatch(batch); deleted += batch.size(); }
            writeWatermark(cursor);
            if (batch.size() < batchSize) break;
        }
        if (file != null) log.info("审计日志归档 {} 行（保留期 {} 天，早于 {}），水位推进到 {}", archived, afterDays, before, cursor);
        return new Result(archived, deleted, cursor, file);
    }

    private void deleteBatch(List<OperationLog> batch) {
        List<Long> ids = batch.stream().map(OperationLog::getId).toList();
        tx.executeWithoutResult(st -> logs.deleteAllByIdInBatch(ids));
    }

    private Path openFile() {
        try {
            Files.createDirectories(Path.of(dir));
            String stamp = LocalDateTime.now().format(STAMP);
            for (int i = 1; i <= 1000; i++) { // 时间戳只到秒，同秒内再跑一次必须换名，否则会重复写表头
                Path p = Path.of(dir, "operation_logs-" + stamp + (i == 1 ? "" : "-" + i) + ".csv");
                if (!Files.exists(p)) return p;
            }
            throw new IllegalStateException("归档目录下同名文件过多: " + stamp);
        } catch (IOException e) {
            throw new IllegalStateException("归档目录不可用: " + dir, e);
        }
    }

    private void append(Path file, List<OperationLog> batch, boolean withHeader) {
        try (FileOutputStream fos = new FileOutputStream(file.toFile(), true);
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            if (withHeader) { w.write(HEADER); w.newLine(); }
            for (OperationLog l : batch) { w.write(row(l)); w.newLine(); }
            w.flush();
            fos.getFD().sync(); // 水位只能在 CSV 确认落盘后推进
        } catch (IOException e) {
            throw new IllegalStateException("归档文件写入失败: " + file, e);
        }
    }

    private String row(OperationLog l) {
        return l.getId() + "," + cell(l.getOperationAt() == null ? null : TS.format(l.getOperationAt())) + ","
                + cell(l.getUsername()) + "," + cell(l.getAction()) + "," + cell(l.getTarget()) + ","
                + cell(l.getMethod()) + "," + cell(l.getPath()) + "," + cell(l.getResult()) + ","
                + cell(l.getMessage()) + "," + cell(l.getRequestBody());
    }

    /** target/path/message 源自请求内容，可能被构造成 Excel 公式：首字符为 = + - @ 或制表符时前置单引号。 */
    private String cell(String raw) {
        if (raw == null) return "\"\"";
        String s = raw.replace('\r', ' ').replace('\n', ' ');
        if (!s.isEmpty()) {
            char c0 = s.charAt(0);
            if (c0 == '=' || c0 == '+' || c0 == '-' || c0 == '@' || c0 == '\t') s = "'" + s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    private long readWatermark() {
        Path p = Path.of(dir, WATERMARK_FILE);
        if (!Files.exists(p)) return 0L;
        try {
            String s = Files.readString(p, StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? 0L : Long.parseLong(s);
        } catch (IOException | NumberFormatException e) {
            log.warn("归档水位读取失败，按 0 重新扫描: {}", e.getMessage());
            return 0L;
        }
    }

    private void writeWatermark(long id) {
        try {
            Files.writeString(Path.of(dir, WATERMARK_FILE), Long.toString(id), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            // 水位写失败只可能导致下轮重复导出，不影响数据完整性
            log.warn("归档水位写入失败: {}", e.getMessage());
        }
    }
}

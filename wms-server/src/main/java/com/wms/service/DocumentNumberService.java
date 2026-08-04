package com.wms.service;

import com.wms.model.entity.DocumentSequence;
import com.wms.repository.DocumentSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 持久化单据号生成器：
 *   RKD / CKD / DBD / PD / BSS / BSY 等。
 * 通过 document_sequences 表 + 行锁取号，避免重启后 AtomicLong 重置导致重号。
 */
@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository sequences;

    public DocumentNumberService(DocumentSequenceRepository sequences) {
        this.sequences = sequences;
    }

    @Transactional
    public String next(String prefix) {
        // 原子插入序号行（并发首次取号也不会重复建行），随后行锁取号递增
        sequences.insertIfAbsent(prefix);
        DocumentSequence seq = sequences.findForUpdate(prefix)
                .orElseThrow(() -> new IllegalStateException("序号行创建失败: " + prefix));
        seq.setCounter(seq.getCounter() + 1);
        sequences.save(seq);
        return prefix + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", seq.getCounter());
    }
}
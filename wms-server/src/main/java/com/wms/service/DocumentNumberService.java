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
        DocumentSequence seq = sequences.findForUpdate(prefix)
                .orElseGet(() -> sequences.save(new DocumentSequence(prefix, 0L)));
        seq.setCounter(seq.getCounter() + 1);
        sequences.save(seq);
        return prefix + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", seq.getCounter());
    }
}
package com.wms.service;

import com.wms.model.entity.DocumentSequence;
import com.wms.repository.DocumentSequenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DocumentNumberServiceTest {

    @Autowired private DocumentNumberService numbers;
    @Autowired private DocumentSequenceRepository sequences;

    @Test
    @Transactional
    void nextSavesCounterAndIncrements() {
        String a = numbers.next("TEST");
        String b = numbers.next("TEST");
        String c = numbers.next("TEST");
        assertTrue(a.matches("TEST-\\d{8}-0001"));
        assertTrue(b.matches("TEST-\\d{8}-0002"));
        assertTrue(c.matches("TEST-\\d{8}-0003"));
        DocumentSequence seq = sequences.findByPrefix("TEST").orElseThrow();
        assertEquals(3L, seq.getCounter());
    }
}
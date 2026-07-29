package com.wms.service;
import org.springframework.stereotype.Service; import java.time.*; import java.time.format.DateTimeFormatter; import java.util.concurrent.atomic.AtomicLong;
@Service public class DocumentNumberService {private final AtomicLong sequence=new AtomicLong();public String next(String prefix){return prefix+"-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%04d",sequence.incrementAndGet());}}

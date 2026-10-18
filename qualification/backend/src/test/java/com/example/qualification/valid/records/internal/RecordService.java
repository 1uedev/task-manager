package com.example.qualification.valid.records.internal;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.qualification.valid.records.Records;

@Service
class RecordService implements Records {
    private final RecordRepository repository;
    RecordService(RecordRepository repository) { this.repository = repository; }
    @Override
    @Transactional
    public void create(UUID id, String label) {
        // Flush makes the row visible to JDBC in the same transaction before its FK insert.
        repository.saveAndFlush(new StoredRecord(id, label));
    }
}

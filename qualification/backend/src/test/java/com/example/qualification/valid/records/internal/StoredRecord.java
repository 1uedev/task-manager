package com.example.qualification.valid.records.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "probe_record")
public class StoredRecord {
    @Id private UUID id;
    private String label;
    @Version private Long version;
    protected StoredRecord() {}
    StoredRecord(UUID id, String label) { this.id = id; this.label = label; }
}

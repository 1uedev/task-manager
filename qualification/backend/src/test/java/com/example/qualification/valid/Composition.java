package com.example.qualification.valid;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.qualification.valid.records.Records;
import com.example.qualification.valid.projection.Projection;

@Service
public class Composition {
    private final Records records;
    private final Projection projection;
    public Composition(Records records, Projection projection) {
        this.records = records;
        this.projection = projection;
    }
    @Transactional
    public void create(UUID id, String label, String indexedText, boolean failAfterWrites) {
        records.create(id, label);
        projection.index(id, indexedText);
        if (failAfterWrites) throw new IllegalStateException("Deliberate qualification failure");
    }
}

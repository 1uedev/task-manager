package com.example.qualification.valid.records.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecordRepository extends JpaRepository<StoredRecord, UUID> {}

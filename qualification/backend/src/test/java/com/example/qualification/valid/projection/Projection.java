package com.example.qualification.valid.projection;

import java.util.UUID;

public interface Projection {
    void index(UUID id, String text);
}

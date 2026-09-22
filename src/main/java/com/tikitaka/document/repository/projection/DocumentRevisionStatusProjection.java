package com.tikitaka.document.repository.projection;

import java.util.UUID;

public interface DocumentRevisionStatusProjection {

    UUID getRevisionId();

    UUID getDocumentId();

    String getStatus();
}

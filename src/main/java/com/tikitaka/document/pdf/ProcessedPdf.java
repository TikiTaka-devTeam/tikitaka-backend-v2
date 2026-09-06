package com.tikitaka.document.pdf;

import java.util.List;

public record ProcessedPdf(byte[] originalBytes, List<byte[]> pageThumbnails) {
    public ProcessedPdf {
        originalBytes = originalBytes.clone();
        pageThumbnails = pageThumbnails.stream().map(byte[]::clone).toList();
    }

    public int pageCount() {
        return pageThumbnails.size();
    }
}

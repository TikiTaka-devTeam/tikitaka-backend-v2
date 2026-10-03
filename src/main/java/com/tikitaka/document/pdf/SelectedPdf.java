package com.tikitaka.document.pdf;

import java.util.Map;
import java.util.stream.Collectors;

public record SelectedPdf(int pageCount, Map<Integer, byte[]> pageThumbnails) {
    public SelectedPdf {
        pageThumbnails = pageThumbnails.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().clone()));
    }
}

package com.tikitaka.global.s3;

import java.text.Normalizer;
import java.util.UUID;

public final class S3ObjectNames {
    private S3ObjectNames() {
    }

    public static String imageFilename(String name, String purpose, String extension) {
        String label = name == null ? "" : Normalizer.normalize(name, Normalizer.Form.NFC);
        label = label.replaceAll("[^\\p{L}\\p{N}_-]+", "_").replaceAll("^_+|_+$", "");
        if (label.isBlank()) {
            label = "unnamed";
        }
        int length = label.codePointCount(0, label.length());
        if (length > 80) {
            label = label.substring(0, label.offsetByCodePoints(0, 80));
        }
        return label + "_" + purpose + "_" + UUID.randomUUID() + extension;
    }
}

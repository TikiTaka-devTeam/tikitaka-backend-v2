package com.tikitaka.question.util;

public final class VectorUtils {

    private VectorUtils() {
    }

    public static String toPgVector(float[] vector) {

        if (vector == null) {
            throw new IllegalArgumentException("Vector must not be null.");
        }

        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < vector.length; i++) {

            if (i > 0) {
                builder.append(",");
            }

            builder.append(vector[i]);
        }

        builder.append("]");

        return builder.toString();
    }
}
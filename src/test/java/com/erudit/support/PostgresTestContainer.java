package com.erudit.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestContainer {
    private static final String IMAGE_PROPERTY = "test.postgres.image";

    private PostgresTestContainer() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PostgreSQLContainer create() {
        String image = System.getProperty(IMAGE_PROPERTY);
        if (image == null || image.isBlank()) {
            throw new IllegalStateException(IMAGE_PROPERTY + " must be configured by the build");
        }
        return new PostgreSQLContainer(image);
    }
}

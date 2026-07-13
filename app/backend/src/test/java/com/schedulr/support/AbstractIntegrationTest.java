package com.schedulr.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

// Singleton container pattern: a static field inherited from this superclass is one shared
// storage slot across every subclass (Java doesn't duplicate static state per subclass), so
// using @Testcontainers + @Container here would let JUnit's lifecycle extension stop the
// container after the first test class finishes, breaking every subsequent test class that
// shares it. Starting it manually and leaving it running (Ryuk reaps it at JVM exit) avoids
// that and lets every integration test class reuse the same container.
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES;

  static {
    POSTGRES = new PostgreSQLContainer<>("postgres:16");
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("jwt.secret", () -> "test-secret-value");
    registry.add("jwt.expiration-minutes", () -> "60");
    registry.add("cors.origin", () -> "http://localhost:3000");
  }
}
